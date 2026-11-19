package com.nexo.service;

import com.nexo.dto.pdi.AtualizarStatusAcaoRequest;
import com.nexo.dto.pdi.PdiAcaoRequest;
import com.nexo.dto.pdi.PdiAcaoResponse;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.entity.Pdi;
import com.nexo.entity.PdiAcao;
import com.nexo.entity.PdiItem;
import com.nexo.entity.StatusAcaoPdi;
import com.nexo.entity.TipoAcaoPdi;
import com.nexo.event.TrilhaConcluidaEvent;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.PdiAcaoRepository;
import com.nexo.repository.PdiItemRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.pdi.AcaoPdiFactory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio das ações de um item do PDI.
 *
 * <p>A criação passa pela {@link AcaoPdiFactory} (Factory Method por tipo): a
 * ação {@code TRILHA} também atribui a trilha ao colaborador. O status dessa
 * ação acompanha a trilha — muda só quando a trilha é concluída
 * ({@link #aoConcluirTrilha}, Observer); as demais ações têm status manual.</p>
 *
 * <p>Bean sem estado (Singleton do Spring).</p>
 */
@Service
public class PdiAcaoService {

    private final PdiService pdiService;
    private final PdiItemRepository pdiItemRepository;
    private final PdiAcaoRepository pdiAcaoRepository;
    private final TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;
    private final AcaoPdiFactory acaoPdiFactory;

    public PdiAcaoService(PdiService pdiService,
                          PdiItemRepository pdiItemRepository,
                          PdiAcaoRepository pdiAcaoRepository,
                          TrilhaAtribuicaoRepository trilhaAtribuicaoRepository,
                          AcaoPdiFactory acaoPdiFactory) {
        this.pdiService = pdiService;
        this.pdiItemRepository = pdiItemRepository;
        this.pdiAcaoRepository = pdiAcaoRepository;
        this.trilhaAtribuicaoRepository = trilhaAtribuicaoRepository;
        this.acaoPdiFactory = acaoPdiFactory;
    }

    @Transactional
    public PdiAcaoResponse adicionar(UUID pdiId, UUID itemId, PdiAcaoRequest request,
                                     UsuarioAutenticado requisitante) {
        PdiItem item = buscarItem(pdiService.buscarEditavel(pdiId, requisitante), itemId);
        PdiAcao acao = pdiAcaoRepository.saveAndFlush(acaoPdiFactory.criar(item, request, requisitante));
        return responder(acao);
    }

    /** Muda o status de uma ação que não é de trilha (ex.: mentoria realizada). */
    @Transactional
    public PdiAcaoResponse atualizarStatus(UUID pdiId, UUID itemId, UUID acaoId,
                                           AtualizarStatusAcaoRequest request,
                                           UsuarioAutenticado requisitante) {
        PdiItem item = buscarItem(pdiService.buscarEditavel(pdiId, requisitante), itemId);
        PdiAcao acao = pdiAcaoRepository.findById(acaoId)
                .filter(a -> a.getPdiItem().getId().equals(item.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Ação não encontrada: " + acaoId));
        if (acao.getTipo() == TipoAcaoPdi.TRILHA) {
            throw new OperacaoNaoPermitidaException(
                    "O status de uma ação de trilha acompanha a trilha: é concluída quando o colaborador conclui a trilha");
        }
        acao.mudarStatus(request.status(), OffsetDateTime.now());
        return responder(pdiAcaoRepository.saveAndFlush(acao));
    }

    /**
     * Observer: quando o colaborador conclui uma trilha, conclui as ações
     * {@code TRILHA} dos PDIs dele ligadas a essa trilha. Roda na mesma
     * transação de quem publicou o evento.
     */
    @EventListener
    @Transactional
    public void aoConcluirTrilha(TrilhaConcluidaEvent evento) {
        List<PdiAcao> acoes = pdiAcaoRepository.acoesDeTrilhaEmAberto(
                evento.tenantId(), evento.trilhaId(), evento.usuarioId());
        acoes.forEach(acao -> acao.mudarStatus(StatusAcaoPdi.CONCLUIDA, evento.concluidoEm()));
        pdiAcaoRepository.saveAll(acoes);
    }

    private PdiItem buscarItem(Pdi pdi, UUID itemId) {
        return pdiItemRepository.findById(itemId)
                .filter(item -> item.getPdi().getId().equals(pdi.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Item do PDI não encontrado: " + itemId));
    }

    private PdiAcaoResponse responder(PdiAcao acao) {
        ProgressoTrilhaResponse progresso = acao.getTrilha() == null
                ? null
                : trilhaAtribuicaoRepository
                        .findByTrilhaIdAndUsuarioId(acao.getTrilha().getId(),
                                acao.getPdiItem().getPdi().getColaborador().getId())
                        .map(ProgressoTrilhaResponse::de)
                        .orElse(null);
        return PdiAcaoResponse.de(acao, progresso);
    }
}
