package com.nexo.service;

import com.nexo.dto.trilha.AtribuicaoEmLoteResponse;
import com.nexo.dto.trilha.AtribuirTrilhaRequest;
import com.nexo.dto.trilha.AtualizarProgressoRequest;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.repository.EtapaProgressoRepository;
import com.nexo.repository.TrilhaEtapaRepository;
import com.nexo.entity.Perfil;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.event.AtribuicaoAtrasadaEvent;
import com.nexo.event.TrilhaAtribuidaEvent;
import com.nexo.event.TrilhaConcluidaEvent;
import com.nexo.event.TrilhaIniciadaEvent;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio das atribuições de trilha (Cards 07 e 42).
 *
 * <p>Há dois caminhos para uma trilha chegar a alguém, e eles têm donos
 * diferentes:</p>
 *
 * <ul>
 *   <li><strong>o próprio usuário</strong> — {@link #iniciar}, {@link #atualizar},
 *       {@link #concluir} e as consultas. Nenhum deles aceita id de usuário
 *       vindo do cliente: o dono do progresso é sempre o {@link UsuarioAutenticado},
 *       o que impede por construção alterar o progresso de outra pessoa;</li>
 *   <li><strong>gestão</strong> — {@link #atribuir}, exclusivo de ADMIN/GESTOR
 *       (restrição aplicada no controller), que atribui a trilha a outros.</li>
 * </ul>
 *
 * <p>O status gravado pode estar desatualizado em relação ao prazo até a rotina
 * diária rodar; por isso toda leitura passa por {@link RegraDeAtraso}, nunca pelo
 * {@code getStatus()} cru.</p>
 */
@Service
public class TrilhaAtribuicaoService {

    private final TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;
    private final TrilhaRepository trilhaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AreaRepository areaRepository;
    private final RegraDeAtraso regraDeAtraso;
    private final ApplicationEventPublisher publisher;
    private final TrilhaEtapaRepository trilhaEtapaRepository;
    private final EtapaProgressoRepository etapaProgressoRepository;
    private final CalculadoraDePercentual calculadora;

    public TrilhaAtribuicaoService(TrilhaAtribuicaoRepository trilhaAtribuicaoRepository,
                                    TrilhaRepository trilhaRepository,
                                    UsuarioRepository usuarioRepository,
                                    AreaRepository areaRepository,
                                    RegraDeAtraso regraDeAtraso,
                                    ApplicationEventPublisher publisher,
                                    TrilhaEtapaRepository trilhaEtapaRepository,
                                    EtapaProgressoRepository etapaProgressoRepository,
                                    CalculadoraDePercentual calculadora) {
        this.trilhaEtapaRepository = trilhaEtapaRepository;
        this.etapaProgressoRepository = etapaProgressoRepository;
        this.calculadora = calculadora;
        this.trilhaAtribuicaoRepository = trilhaAtribuicaoRepository;
        this.trilhaRepository = trilhaRepository;
        this.usuarioRepository = usuarioRepository;
        this.areaRepository = areaRepository;
        this.regraDeAtraso = regraDeAtraso;
        this.publisher = publisher;
    }

    /**
     * Atribui a trilha a uma lista de colaboradores ou a todos os ativos de uma
     * área. Quem já tem a trilha é ignorado — e, importante, mantém o progresso
     * que já fez: reatribuir nunca zera ninguém.
     */
    @Transactional
    public AtribuicaoEmLoteResponse atribuir(UUID trilhaId, AtribuirTrilhaRequest request,
                                              UsuarioAutenticado requisitante) {
        Trilha trilha = buscarTrilhaNoTenant(trilhaId, requisitante.getTenantId());
        if (!trilha.isAtivo()) {
            throw new OperacaoNaoPermitidaException(
                    "Trilha inativa não pode receber novas atribuições: " + trilha.getNome());
        }

        List<Usuario> destinatarios = resolverDestinatarios(request, requisitante.getTenantId());
        LocalDate prazo = resolverPrazo(request.prazo(), trilha);
        Usuario quemAtribuiu = usuarioRepository.getReferenceById(requisitante.getId());

        List<UUID> atribuidos = new ArrayList<>();
        int ignorados = 0;
        for (Usuario destinatario : destinatarios) {
            if (trilhaAtribuicaoRepository.existsByTrilhaIdAndUsuarioId(trilha.getId(), destinatario.getId())) {
                ignorados++;
                continue;
            }
            trilhaAtribuicaoRepository.save(TrilhaAtribuicao.nova(
                    trilha, destinatario, quemAtribuiu, prazo, StatusAtribuicao.NAO_INICIADA));
            atribuidos.add(destinatario.getId());
        }
        trilhaAtribuicaoRepository.flush();

        if (!atribuidos.isEmpty()) {
            publisher.publishEvent(new TrilhaAtribuidaEvent(
                    trilha.getId(), trilha.getTenant().getId(), atribuidos, requisitante.getId()));
        }
        return AtribuicaoEmLoteResponse.de(atribuidos, ignorados);
    }

    /**
     * Marca como {@code ATRASADA} toda atribuição com prazo vencido que ainda
     * não foi concluída, e anuncia cada uma. Idempotente: rodar duas vezes no
     * mesmo dia não muda nada na segunda, porque quem já está atrasada sai do
     * filtro de status.
     *
     * @return quantas foram marcadas
     */
    @Transactional
    public int marcarAtrasadas() {
        List<TrilhaAtribuicao> vencidas = trilhaAtribuicaoRepository
                .findByPrazoBeforeAndStatusNotIn(
                        regraDeAtraso.hoje(),
                        List.of(StatusAtribuicao.CONCLUIDA, StatusAtribuicao.ATRASADA));

        vencidas.forEach(atribuicao -> {
            atribuicao.setStatus(StatusAtribuicao.ATRASADA);
            publisher.publishEvent(new AtribuicaoAtrasadaEvent(
                    atribuicao.getId(),
                    atribuicao.getTenant().getId(),
                    atribuicao.getTrilha().getId(),
                    atribuicao.getUsuario().getId(),
                    atribuicao.getPrazo()));
        });
        trilhaAtribuicaoRepository.saveAll(vencidas);
        return vencidas.size();
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
        var existente = trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId());
        if (!trilha.isAtivo() && existente.isEmpty()) {
            throw new OperacaoNaoPermitidaException("Trilha inativa não pode ser iniciada sem uma atribuição existente");
        }
        if (existente.isPresent()) {
            return comecarSeAindaNaoComecou(existente.get());
        }
        return responder(criarProgresso(trilha, requisitante));
    }

    /** Atualiza o status do progresso já existente do requisitante na trilha. */
    @Transactional
    public ProgressoTrilhaResponse atualizar(UUID trilhaId, AtualizarProgressoRequest request,
                                              UsuarioAutenticado requisitante) {
        TrilhaAtribuicao progresso = buscarProgressoDoRequisitante(trilhaId, requisitante);
        boolean concluirAgora = progresso.getStatus() != StatusAtribuicao.CONCLUIDA
                && request.status() == StatusAtribuicao.CONCLUIDA;
        aplicarStatus(progresso, request.status());
        return responder(gravar(progresso, concluirAgora));
    }

    /**
     * Marca o progresso do requisitante na trilha como concluído agora.
     * Concluir uma trilha atrasada é permitido: ela vira {@code CONCLUIDA} e
     * deixa de ser atrasada, porque o prazo já não significa nada depois disso.
     */
    @Transactional
    public ProgressoTrilhaResponse concluir(UUID trilhaId, UsuarioAutenticado requisitante) {
        TrilhaAtribuicao progresso = buscarProgressoDoRequisitante(trilhaId, requisitante);
        boolean concluirAgora = progresso.getStatus() != StatusAtribuicao.CONCLUIDA;
        aplicarStatus(progresso, StatusAtribuicao.CONCLUIDA);
        return responder(gravar(progresso, concluirAgora));
    }

    /** Busca o progresso do requisitante na trilha. Nunca cria — use {@link #iniciar} para isso. */
    @Transactional(readOnly = true)
    public ProgressoTrilhaResponse buscarProgresso(UUID trilhaId, UsuarioAutenticado requisitante) {
        return responder(buscarProgressoDoRequisitante(trilhaId, requisitante));
    }

    /**
     * Progresso do requisitante numa trilha já resolvida no tenant (Card 08:
     * detalhe da trilha), ou "não iniciado" se ele nunca acessou — ao
     * contrário de {@link #buscarProgresso}, nunca lança 404 por falta de
     * registro.
     */
    @Transactional(readOnly = true)
    public ProgressoTrilhaResponse buscarProgressoOuNaoIniciado(Trilha trilha, UsuarioAutenticado requisitante) {
        return trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilha.getId(), requisitante.getId())
                .map(this::responder)
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
                .collect(Collectors.toMap(a -> a.getTrilha().getId(), this::responder));
    }

    /**
     * Atribuição que o gestor criou nasce {@code NAO_INICIADA}; quando o
     * colaborador clica em "iniciar", ela precisa de fato começar. Sem isto, o
     * endpoint respondia 200 e a trilha continuava parada em "não iniciada".
     *
     * <p>Qualquer outro status é devolvido como está: iniciar é idempotente e
     * não pode reabrir uma trilha concluída nem apagar o progresso de quem já
     * estava em andamento.</p>
     */
    private ProgressoTrilhaResponse comecarSeAindaNaoComecou(TrilhaAtribuicao atribuicao) {
        if (atribuicao.getStatus() == StatusAtribuicao.CONCLUIDA) {
            return responder(atribuicao);
        }
        boolean precisaRegistrarInicio = atribuicao.getIniciadoEm() == null;
        boolean estadoMudou = atribuicao.getStatus() == StatusAtribuicao.NAO_INICIADA;
        if (estadoMudou) {
            atribuicao.setStatus(StatusAtribuicao.EM_ANDAMENTO);
        }
        if (estadoMudou || precisaRegistrarInicio) {
            return responder(gravar(atribuicao, false));
        }
        return responder(atribuicao);
    }

    /** Toda resposta sai daqui, para nenhuma leitura escapar da regra de prazo. */
    private ProgressoTrilhaResponse responder(TrilhaAtribuicao progresso) {
        return ProgressoTrilhaResponse.de(progresso)
                .comStatus(regraDeAtraso.statusEfetivo(progresso.getStatus(), progresso.getPrazo()));
    }

    private List<Usuario> resolverDestinatarios(AtribuirTrilhaRequest request, UUID tenantId) {
        if (request.temArea()) {
            // Área inexistente ou de outro tenant é 404, como no resto do
            // projeto — sem isto, cairia no 409 de "área sem colaboradores".
            areaRepository.findById(request.areaId())
                    .filter(area -> area.getTenant().getId().equals(tenantId))
                    .orElseThrow(() -> new ResourceNotFoundException("Área não encontrada: " + request.areaId()));

            // Só colaborador cursa trilha: gestor e administrador da área ficam de
            // fora, senão ganhariam uma pendência que não têm como cumprir.
            List<Usuario> daArea = usuarioRepository.findByTenantIdAndAreaIdAndAtivoTrue(tenantId, request.areaId())
                    .stream()
                    .filter(usuario -> usuario.getPapel() == Perfil.COLABORADOR)
                    .toList();
            if (daArea.isEmpty()) {
                throw new OperacaoNaoPermitidaException("A área não tem colaboradores ativos para receber a trilha");
            }
            return daArea;
        }
        return request.usuarioIds().stream().distinct().map(id -> buscarColaborador(id, tenantId)).toList();
    }

    private Usuario buscarColaborador(UUID usuarioId, UUID tenantId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .filter(u -> u.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + usuarioId));
        if (!usuario.isAtivo()) {
            throw new OperacaoNaoPermitidaException(
                    "Usuário inativo não pode receber trilha: " + usuario.getEmail());
        }
        if (usuario.getPapel() != Perfil.COLABORADOR) {
            throw new OperacaoNaoPermitidaException(
                    "Só colaboradores recebem trilhas; " + usuario.getNomeCompleto()
                            + " tem perfil de gestão");
        }
        return usuario;
    }

    /** Sem prazo no pedido, vale o prazo relativo da trilha ("N dias" a partir de hoje). */
    private LocalDate resolverPrazo(LocalDate prazoPedido, Trilha trilha) {
        if (prazoPedido != null) {
            return prazoPedido;
        }
        return trilha.getPrazoDias() == null ? null : regraDeAtraso.hoje().plusDays(trilha.getPrazoDias());
    }

    /**
     * Grava e, se a trilha ficou concluída, avisa quem se interessa
     * ({@link TrilhaConcluidaEvent} — ex.: o PDI conclui a ação ligada a ela).
     */
    private TrilhaAtribuicao gravar(TrilhaAtribuicao progresso, boolean publicarConclusao) {
        // iniciado_em permite detectar a primeira transição de início mesmo
        // quando o status persistido virou ATRASADA pela rotina diária.
        boolean primeiroInicio = progresso.getIniciadoEm() == null
                && progresso.getStatus() != StatusAtribuicao.NAO_INICIADA;
        if (primeiroInicio) {
            progresso.setIniciadoEm(OffsetDateTime.now());
        }
        TrilhaAtribuicao gravado = trilhaAtribuicaoRepository.saveAndFlush(progresso);
        if (primeiroInicio) {
            publisher.publishEvent(new TrilhaIniciadaEvent(
                    gravado.getTrilha().getId(), gravado.getTrilha().getNome(),
                    gravado.getTenant().getId(), gravado.getUsuario().getId()));
        }
        if (publicarConclusao && gravado.getStatus() == StatusAtribuicao.CONCLUIDA) {
            publisher.publishEvent(new TrilhaConcluidaEvent(
                    gravado.getTenant().getId(),
                    gravado.getTrilha().getId(),
                    gravado.getUsuario().getId(),
                    gravado.getConcluidoEm()));
        }
        return gravado;
    }

    private TrilhaAtribuicao criarProgresso(Trilha trilha, UsuarioAutenticado requisitante) {
        Usuario usuarioRef = usuarioRepository.getReferenceById(requisitante.getId());
        // Auto-início: quem atribui é o próprio usuário.
        TrilhaAtribuicao progresso = TrilhaAtribuicao.nova(
                trilha, usuarioRef, usuarioRef, resolverPrazo(null, trilha), StatusAtribuicao.EM_ANDAMENTO);
        return gravar(progresso, false);
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
        if (novoStatus == StatusAtribuicao.ATRASADA) {
            throw new OperacaoNaoPermitidaException(
                    "ATRASADA é determinado pelo prazo e não pode ser definido manualmente");
        }
        StatusAtribuicao statusAnterior = progresso.getStatus();
        long totalDeEtapas = trilhaEtapaRepository.countByTrilhaId(progresso.getTrilha().getId());
        long concluidas = totalDeEtapas == 0 ? 0 : etapaProgressoRepository.contarConcluidas(
                progresso.getTrilha().getId(), progresso.getUsuario().getId());
        if (novoStatus == StatusAtribuicao.CONCLUIDA && statusAnterior != StatusAtribuicao.CONCLUIDA
                && concluidas < totalDeEtapas) {
            // Trilha com etapas se conclui pelas etapas: marcar direto pularia o conteúdo.
            long faltam = totalDeEtapas - concluidas;
            throw new OperacaoNaoPermitidaException(
                    "Conclua todas as etapas antes de concluir a trilha: "
                            + (faltam == 1 ? "falta 1 etapa" : "faltam " + faltam + " etapas"));
        }
        progresso.setStatus(novoStatus);
        if (totalDeEtapas > 0 && novoStatus != StatusAtribuicao.CONCLUIDA) {
            // Retomar a trilha não desfaz etapas: o percentual volta a ser o delas.
            progresso.setPercentual(calculadora.percentual(concluidas, totalDeEtapas));
        }
        // Repetir a conclusão é idempotente e preserva a data original.
        // Regressão para outro estado invalida a conclusão anterior.
        if (novoStatus == StatusAtribuicao.CONCLUIDA) {
            if (statusAnterior != StatusAtribuicao.CONCLUIDA || progresso.getConcluidoEm() == null) {
                progresso.setConcluidoEm(OffsetDateTime.now());
            }
        } else {
            progresso.setConcluidoEm(null);
        }
    }
}
