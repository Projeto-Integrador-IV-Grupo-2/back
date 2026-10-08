package com.nexo.service;

import com.nexo.dto.trilha.AtualizarProgressoRequest;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.event.TrilhaConcluidaEvent;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio do progresso de um usuário numa trilha (Card 07).
 *
 * <p><strong>Sempre o próprio usuário:</strong> nenhum método aqui aceita um
 * id de usuário vindo do cliente — o dono do progresso é sempre
 * {@link UsuarioAutenticado}, extraído do contexto de segurança pelo
 * controller. Isso já impede, por construção, que alguém altere o progresso
 * de outro usuário: não há como nomear "outro usuário" numa chamada.</p>
 *
 * <p>Acompanhamento de equipe por Gestor/Admin (ver histórico do Card 07) foi
 * avaliado e fica fora deste card: os quatro métodos pedidos (iniciar,
 * atualizar, concluir, buscar) são todos sobre o progresso do próprio
 * requisitante. Uma tela de gestor acompanhando a equipe é uma consulta
 * diferente (lista o progresso de vários usuários), não uma exceção a estas
 * regras — fica para quando essa necessidade de produto existir.</p>
 */
@Service
public class TrilhaAtribuicaoService {

    private final TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;
    private final TrilhaRepository trilhaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApplicationEventPublisher eventos;

    public TrilhaAtribuicaoService(TrilhaAtribuicaoRepository trilhaAtribuicaoRepository,
                                    TrilhaRepository trilhaRepository,
                                    UsuarioRepository usuarioRepository,
                                    ApplicationEventPublisher eventos) {
        this.trilhaAtribuicaoRepository = trilhaAtribuicaoRepository;
        this.trilhaRepository = trilhaRepository;
        this.usuarioRepository = usuarioRepository;
        this.eventos = eventos;
    }

    /**
     * Retorna o progresso do requisitante na trilha, criando-o (status
     * {@code EM_ANDAMENTO}) se esta for a primeira interação dele com ela.
     *
     * <p>Uma trilha atribuída por outra pessoa (ex.: pelo gestor, via ação do
     * PDI) já tem registro em {@code NAO_INICIADA}: iniciar a promove para
     * {@code EM_ANDAMENTO}, mantendo o prazo e quem atribuiu.</p>
     */
    @Transactional
    public ProgressoTrilhaResponse iniciar(UUID trilhaId, UsuarioAutenticado requisitante) {
        Trilha trilha = buscarTrilhaNoTenant(trilhaId, requisitante.getTenantId());
        return trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId())
                .map(this::comecarSeNaoIniciada)
                .map(ProgressoTrilhaResponse::de)
                .orElseGet(() -> ProgressoTrilhaResponse.de(criarProgresso(trilha, requisitante)));
    }

    /** Atualiza o status do progresso já existente do requisitante na trilha. */
    @Transactional
    public ProgressoTrilhaResponse atualizar(UUID trilhaId, AtualizarProgressoRequest request,
                                              UsuarioAutenticado requisitante) {
        TrilhaAtribuicao progresso = buscarProgressoDoRequisitante(trilhaId, requisitante);
        aplicarStatus(progresso, request.status());
        return ProgressoTrilhaResponse.de(gravar(progresso));
    }

    /** Marca o progresso do requisitante na trilha como concluído agora. */
    @Transactional
    public ProgressoTrilhaResponse concluir(UUID trilhaId, UsuarioAutenticado requisitante) {
        TrilhaAtribuicao progresso = buscarProgressoDoRequisitante(trilhaId, requisitante);
        aplicarStatus(progresso, StatusAtribuicao.CONCLUIDA);
        return ProgressoTrilhaResponse.de(gravar(progresso));
    }

    /** Busca o progresso do requisitante na trilha. Nunca cria — use {@link #iniciar} para isso. */
    @Transactional(readOnly = true)
    public ProgressoTrilhaResponse buscarProgresso(UUID trilhaId, UsuarioAutenticado requisitante) {
        return ProgressoTrilhaResponse.de(buscarProgressoDoRequisitante(trilhaId, requisitante));
    }

    /**
     * Progresso do requisitante numa trilha já resolvida no tenant (Card 08:
     * detalhe da trilha), ou "não iniciado" se ele nunca acessou — ao
     * contrário de {@link #buscarProgresso}, nunca lança 404 por falta de
     * registro. Recebe a {@link Trilha} já carregada porque quem chama (ex.:
     * {@code TrilhaService}) já validou tenant/existência; evita repetir essa
     * consulta aqui.
     */
    @Transactional(readOnly = true)
    public ProgressoTrilhaResponse buscarProgressoOuNaoIniciado(Trilha trilha, UsuarioAutenticado requisitante) {
        return trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilha.getId(), requisitante.getId())
                .map(ProgressoTrilhaResponse::de)
                .orElseGet(() -> ProgressoTrilhaResponse.naoIniciado(trilha));
    }

    /**
     * Progresso do requisitante em todas as trilhas que ele já iniciou,
     * indexado pelo id da trilha — uma consulta só, para a listagem não fazer
     * uma ida ao banco por trilha. Trilha ausente do mapa = não iniciada.
     */
    @Transactional(readOnly = true)
    public Map<UUID, ProgressoTrilhaResponse> progressosDoRequisitante(UsuarioAutenticado requisitante) {
        return trilhaAtribuicaoRepository
                .findByTenantIdAndUsuarioId(requisitante.getTenantId(), requisitante.getId())
                .stream()
                .collect(Collectors.toMap(a -> a.getTrilha().getId(), ProgressoTrilhaResponse::de));
    }

    private TrilhaAtribuicao comecarSeNaoIniciada(TrilhaAtribuicao progresso) {
        if (progresso.getStatus() != StatusAtribuicao.NAO_INICIADA) {
            return progresso;
        }
        progresso.setStatus(StatusAtribuicao.EM_ANDAMENTO);
        return trilhaAtribuicaoRepository.saveAndFlush(progresso);
    }

    /**
     * Grava e, se a trilha ficou concluída, avisa quem se interessa
     * ({@link TrilhaConcluidaEvent} — ex.: o PDI conclui a ação ligada a ela).
     */
    private TrilhaAtribuicao gravar(TrilhaAtribuicao progresso) {
        TrilhaAtribuicao gravado = trilhaAtribuicaoRepository.saveAndFlush(progresso);
        if (gravado.getStatus() == StatusAtribuicao.CONCLUIDA) {
            eventos.publishEvent(new TrilhaConcluidaEvent(
                    gravado.getTenant().getId(),
                    gravado.getTrilha().getId(),
                    gravado.getUsuario().getId(),
                    gravado.getConcluidoEm()));
        }
        return gravado;
    }

    private TrilhaAtribuicao criarProgresso(Trilha trilha, UsuarioAutenticado requisitante) {
        Usuario usuarioRef = usuarioRepository.getReferenceById(requisitante.getId());

        TrilhaAtribuicao progresso = new TrilhaAtribuicao();
        progresso.setTenant(trilha.getTenant());
        progresso.setTrilha(trilha);
        progresso.setUsuario(usuarioRef);
        // Sem fluxo de atribuição por Gestor/Admin implementado ainda: o
        // primeiro acesso é sempre auto-iniciado pelo próprio usuário.
        progresso.setAtribuidoPor(usuarioRef);
        progresso.setStatus(StatusAtribuicao.EM_ANDAMENTO);
        // O prazo da trilha é relativo ("N dias"): vira data a partir do início.
        if (trilha.getPrazoDias() != null) {
            progresso.setPrazo(LocalDate.now().plusDays(trilha.getPrazoDias()));
        }
        return trilhaAtribuicaoRepository.saveAndFlush(progresso);
    }

    private TrilhaAtribuicao buscarProgressoDoRequisitante(UUID trilhaId, UsuarioAutenticado requisitante) {
        // Garante 404 de trilha (não 404 de progresso) quando o id nem é uma trilha válida do tenant.
        buscarTrilhaNoTenant(trilhaId, requisitante.getTenantId());
        return trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Progresso não encontrado para esta trilha"));
    }

    private Trilha buscarTrilhaNoTenant(UUID trilhaId, UUID tenantId) {
        Trilha trilha = trilhaRepository.findById(trilhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Trilha não encontrada: " + trilhaId));
        if (!trilha.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Trilha não encontrada: " + trilhaId);
        }
        return trilha;
    }

    /**
     * {@code NAO_INICIADA} é o estado antes de o colaborador começar (ausência
     * de registro, ou trilha atribuída pelo gestor): uma vez iniciado o
     * progresso, não faz sentido negociá-lo de volta a esse estado.
     */
    private void aplicarStatus(TrilhaAtribuicao progresso, StatusAtribuicao novoStatus) {
        if (novoStatus == StatusAtribuicao.NAO_INICIADA) {
            throw new OperacaoNaoPermitidaException(
                    "Não é possível voltar um progresso já iniciado para NAO_INICIADA");
        }
        progresso.setStatus(novoStatus);
        // Regressão de status (ex.: reabrir uma trilha concluída) invalida a
        // data de conclusão anterior — senão ficaria "concluída" com data velha.
        progresso.setConcluidoEm(novoStatus == StatusAtribuicao.CONCLUIDA ? OffsetDateTime.now() : null);
    }
}
