package com.nexo.service;

import com.nexo.dto.etapa.ConclusaoEtapaResponse;
import com.nexo.dto.etapa.EtapaRequest;
import com.nexo.dto.etapa.EtapaResponse;
import com.nexo.dto.etapa.ReordenarEtapasRequest;
import com.nexo.entity.Conteudo;
import com.nexo.entity.EtapaProgresso;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaEtapa;
import com.nexo.event.EtapaConcluidaEvent;
import com.nexo.event.EtapasDaTrilhaAlteradasEvent;
import com.nexo.exception.ParametroInvalidoException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.EtapaProgressoRepository;
import com.nexo.repository.TrilhaEtapaRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Etapas da trilha e progresso por etapa (issue #40).
 *
 * <p>Montar as etapas (criar, editar, remover, reordenar) é de ADMIN e GESTOR
 * — restrição aplicada no controller. Listar e concluir valem para qualquer
 * usuário do tenant, e a conclusão é sempre do próprio usuário autenticado:
 * nenhum endpoint aceita id de usuário, então ninguém altera o progresso de
 * outra pessoa.</p>
 *
 * <p>Toda mudança que afeta o percentual publica um evento; quem recalcula é o
 * {@link RecalculoDeProgressoListener}.</p>
 */
@Service
public class TrilhaEtapaService {

    private final TrilhaEtapaRepository trilhaEtapaRepository;
    private final EtapaProgressoRepository etapaProgressoRepository;
    private final TrilhaRepository trilhaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConteudoService conteudoService;
    private final TrilhaAtribuicaoService trilhaAtribuicaoService;
    private final ApplicationEventPublisher publisher;

    public TrilhaEtapaService(TrilhaEtapaRepository trilhaEtapaRepository,
                              EtapaProgressoRepository etapaProgressoRepository,
                              TrilhaRepository trilhaRepository,
                              UsuarioRepository usuarioRepository,
                              ConteudoService conteudoService,
                              TrilhaAtribuicaoService trilhaAtribuicaoService,
                              ApplicationEventPublisher publisher) {
        this.trilhaEtapaRepository = trilhaEtapaRepository;
        this.etapaProgressoRepository = etapaProgressoRepository;
        this.trilhaRepository = trilhaRepository;
        this.usuarioRepository = usuarioRepository;
        this.conteudoService = conteudoService;
        this.trilhaAtribuicaoService = trilhaAtribuicaoService;
        this.publisher = publisher;
    }

    /** Etapas em ordem, cada uma marcando se o usuário autenticado já a concluiu. */
    @Transactional(readOnly = true)
    public List<EtapaResponse> listar(UUID trilhaId, UsuarioAutenticado requisitante) {
        buscarTrilhaNoTenant(trilhaId, requisitante.getTenantId());
        Set<UUID> concluidas = new HashSet<>(etapaProgressoRepository.etapasConcluidas(trilhaId, requisitante.getId()));
        return trilhaEtapaRepository.findByTrilhaIdOrderByOrdem(trilhaId).stream()
                .map(etapa -> EtapaResponse.de(etapa, concluidas.contains(etapa.getId())))
                .toList();
    }

    /**
     * Adiciona a etapa no fim da trilha. Quem estava em andamento passa a ter
     * mais uma etapa a fazer, e o percentual dele cai.
     */
    @Transactional
    public EtapaResponse criar(UUID trilhaId, EtapaRequest request, UsuarioAutenticado requisitante) {
        Trilha trilha = buscarTrilhaNoTenant(trilhaId, requisitante.getTenantId());
        Conteudo conteudo = conteudoService.buscarPublicadoNoTenant(request.conteudoId(), requisitante.getTenantId());

        int proximaOrdem = trilhaEtapaRepository.maiorOrdem(trilhaId) + 1;
        TrilhaEtapa etapa = trilhaEtapaRepository.saveAndFlush(
                TrilhaEtapa.nova(trilha, conteudo, request.titulo(), textoOuNulo(request.descricao()), proximaOrdem));

        publisher.publishEvent(new EtapasDaTrilhaAlteradasEvent(trilhaId, requisitante.getTenantId()));
        return EtapaResponse.de(etapa, false);
    }

    /** Troca conteúdo, título ou descrição. Não mexe na ordem nem no progresso. */
    @Transactional
    public EtapaResponse atualizar(UUID trilhaId, UUID etapaId, EtapaRequest request,
                                   UsuarioAutenticado requisitante) {
        buscarTrilhaNoTenant(trilhaId, requisitante.getTenantId());
        TrilhaEtapa etapa = buscarEtapaDaTrilha(trilhaId, etapaId);

        if (!etapa.getConteudo().getId().equals(request.conteudoId())) {
            etapa.setConteudo(conteudoService.buscarPublicadoNoTenant(request.conteudoId(), requisitante.getTenantId()));
        }
        String titulo = request.titulo();
        etapa.setTitulo(titulo == null || titulo.isBlank() ? etapa.getConteudo().getTitulo() : titulo.trim());
        etapa.setDescricao(textoOuNulo(request.descricao()));

        TrilhaEtapa salva = trilhaEtapaRepository.saveAndFlush(etapa);
        boolean concluida = etapaProgressoRepository.existsByEtapaIdAndUsuarioId(etapaId, requisitante.getId());
        return EtapaResponse.de(salva, concluida);
    }

    /**
     * Remove a etapa e o progresso registrado nela, e fecha o buraco na
     * sequência: as etapas seguintes sobem uma posição.
     */
    @Transactional
    public void remover(UUID trilhaId, UUID etapaId, UsuarioAutenticado requisitante) {
        buscarTrilhaNoTenant(trilhaId, requisitante.getTenantId());
        TrilhaEtapa etapa = buscarEtapaDaTrilha(trilhaId, etapaId);
        int ordemRemovida = etapa.getOrdem();

        etapaProgressoRepository.removerDaEtapa(etapaId);
        trilhaEtapaRepository.delete(etapa);
        trilhaEtapaRepository.findByTrilhaIdOrderByOrdem(trilhaId).stream()
                .filter(seguinte -> seguinte.getOrdem() > ordemRemovida)
                .forEach(seguinte -> seguinte.setOrdem(seguinte.getOrdem() - 1));
        trilhaEtapaRepository.flush();

        publisher.publishEvent(new EtapasDaTrilhaAlteradasEvent(trilhaId, requisitante.getTenantId()));
    }

    /**
     * Aplica a nova sequência. O pedido precisa trazer exatamente as etapas da
     * trilha, cada uma uma vez — assim a ordem nunca se repete nem fica com
     * buraco.
     */
    @Transactional
    public List<EtapaResponse> reordenar(UUID trilhaId, ReordenarEtapasRequest request,
                                         UsuarioAutenticado requisitante) {
        buscarTrilhaNoTenant(trilhaId, requisitante.getTenantId());
        List<TrilhaEtapa> etapas = trilhaEtapaRepository.findByTrilhaIdOrderByOrdem(trilhaId);
        List<UUID> novaOrdem = request.etapaIds();

        Set<UUID> pedidas = new HashSet<>(novaOrdem);
        Set<UUID> existentes = etapas.stream().map(TrilhaEtapa::getId).collect(Collectors.toSet());
        if (pedidas.size() != novaOrdem.size() || !pedidas.equals(existentes)) {
            throw new ParametroInvalidoException(
                    "Informe todas as etapas da trilha, cada uma uma única vez, na nova ordem");
        }

        Map<UUID, TrilhaEtapa> porId = etapas.stream()
                .collect(Collectors.toMap(TrilhaEtapa::getId, Function.identity()));
        for (int i = 0; i < novaOrdem.size(); i++) {
            porId.get(novaOrdem.get(i)).setOrdem(i + 1);
        }
        trilhaEtapaRepository.flush();
        return listar(trilhaId, requisitante);
    }

    /**
     * Conclui a etapa para o usuário autenticado. Na primeira etapa, inicia a
     * trilha para ele. Idempotente: concluir de novo não duplica o registro nem
     * altera o percentual.
     */
    @Transactional
    public ConclusaoEtapaResponse concluir(UUID trilhaId, UUID etapaId, UsuarioAutenticado requisitante) {
        buscarTrilhaNoTenant(trilhaId, requisitante.getTenantId());
        TrilhaEtapa etapa = buscarEtapaDaTrilha(trilhaId, etapaId);
        trilhaAtribuicaoService.iniciar(trilhaId, requisitante);

        if (!etapaProgressoRepository.existsByEtapaIdAndUsuarioId(etapaId, requisitante.getId())) {
            etapaProgressoRepository.saveAndFlush(
                    EtapaProgresso.de(etapa, usuarioRepository.getReferenceById(requisitante.getId())));
            publisher.publishEvent(new EtapaConcluidaEvent(
                    etapaId, trilhaId, requisitante.getTenantId(), requisitante.getId()));
        }
        return ConclusaoEtapaResponse.de(etapaId, trilhaAtribuicaoService.buscarProgresso(trilhaId, requisitante));
    }

    private TrilhaEtapa buscarEtapaDaTrilha(UUID trilhaId, UUID etapaId) {
        return trilhaEtapaRepository.findById(etapaId)
                .filter(etapa -> etapa.getTrilha().getId().equals(trilhaId))
                .orElseThrow(() -> new ResourceNotFoundException("Etapa não encontrada: " + etapaId));
    }

    private Trilha buscarTrilhaNoTenant(UUID trilhaId, UUID tenantId) {
        return trilhaRepository.findById(trilhaId)
                .filter(trilha -> trilha.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Trilha não encontrada: " + trilhaId));
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
