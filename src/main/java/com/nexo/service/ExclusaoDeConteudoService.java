package com.nexo.service;

import com.nexo.entity.Arquivo;
import com.nexo.entity.Conteudo;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.ArquivoRepository;
import com.nexo.repository.ConteudoRepository;
import com.nexo.repository.HistoricoInteracaoRepository;
import com.nexo.repository.TrilhaEtapaRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.armazenamento.ArmazenamentoDeArquivos;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Exclusão definitiva de um conteúdo.
 *
 * <p>Trechos da IA, situação da indexação e registros de arquivo caem em
 * cascata no banco; os binários saem do armazenamento só depois que a
 * exclusão é confirmada. O histórico de interações fica, sem apontar para o
 * conteúdo.</p>
 *
 * <p>Conteúdo usado numa etapa de trilha não pode ser excluído: a etapa
 * ficaria sem material.</p>
 */
@Service
public class ExclusaoDeConteudoService {

    private static final Logger log = LoggerFactory.getLogger(ExclusaoDeConteudoService.class);

    private final ConteudoRepository conteudoRepository;
    private final TrilhaEtapaRepository trilhaEtapaRepository;
    private final ArquivoRepository arquivoRepository;
    private final HistoricoInteracaoRepository historicoInteracaoRepository;
    private final ArmazenamentoDeArquivos armazenamento;

    public ExclusaoDeConteudoService(ConteudoRepository conteudoRepository,
                                     TrilhaEtapaRepository trilhaEtapaRepository,
                                     ArquivoRepository arquivoRepository,
                                     HistoricoInteracaoRepository historicoInteracaoRepository,
                                     ArmazenamentoDeArquivos armazenamento) {
        this.conteudoRepository = conteudoRepository;
        this.trilhaEtapaRepository = trilhaEtapaRepository;
        this.arquivoRepository = arquivoRepository;
        this.historicoInteracaoRepository = historicoInteracaoRepository;
        this.armazenamento = armazenamento;
    }

    @Transactional
    public void excluir(UUID id, UsuarioAutenticado requisitante) {
        UUID tenantId = requisitante.getTenantId();
        Conteudo conteudo = conteudoRepository.findById(id)
                .filter(encontrado -> encontrado.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Conteúdo não encontrado: " + id));
        if (trilhaEtapaRepository.existsByConteudoId(conteudo.getId())) {
            throw new OperacaoNaoPermitidaException(
                    "Remova o conteúdo das etapas de trilha antes de excluí-lo: " + conteudo.getTitulo());
        }
        List<Arquivo> arquivos = arquivoRepository
                .findByTenantIdAndConteudoIdOrderByCriadoEmAsc(tenantId, conteudo.getId());
        List<String> chaves = arquivos.stream().map(Arquivo::getChaveArmazenamento).toList();
        historicoInteracaoRepository.desvincularConteudo(conteudo.getId());
        // Os registros de arquivo já estão carregados: saem antes do conteúdo,
        // senão o Hibernate os vê apontando para um conteúdo apagado.
        arquivoRepository.deleteAll(arquivos);
        arquivoRepository.flush();
        conteudoRepository.delete(conteudo);
        conteudoRepository.flush();
        apagarArquivosDepoisDeConfirmar(chaves);
    }

    private void apagarArquivosDepoisDeConfirmar(List<String> chaves) {
        if (chaves.isEmpty()) {
            return;
        }
        Runnable apagar = () -> chaves.forEach(chave -> {
            try {
                armazenamento.remover(chave);
            } catch (IOException | RuntimeException ex) {
                log.warn("Não foi possível apagar o arquivo {} do conteúdo excluído", chave);
            }
        });
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            apagar.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                apagar.run();
            }
        });
    }
}
