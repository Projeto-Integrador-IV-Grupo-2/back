package com.nexo.service.notificacao;

import com.nexo.config.NotificacaoProperties;
import com.nexo.entity.Conteudo;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.TipoNotificacao;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.event.AtribuicaoAtrasadaEvent;
import com.nexo.event.ConteudoPublicadoEvent;
import com.nexo.event.TrilhaAtribuidaEvent;
import com.nexo.event.TrilhaConcluidaEvent;
import com.nexo.event.TrilhaInativadaEvent;
import com.nexo.repository.ConteudoRepository;
import com.nexo.repository.NotificacaoRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transforma os eventos do sistema em notificações guardadas (issue #78).
 *
 * <p>Decide <em>quem</em> recebe cada aviso; o texto e o link de cada tipo
 * são da {@link NotificacaoFactory}. É chamado pelo
 * {@link NotificacoesListener} (eventos) e pela {@link RotinaDeNotificacoes}
 * (prazo próximo e limpeza) — os serviços de trilha e de conteúdo não sabem
 * que notificação existe.</p>
 *
 * <p>Singleton do Spring e sem estado.</p>
 */
@Service
public class GeradorDeNotificacoes {

    private final NotificacaoRepository notificacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TrilhaRepository trilhaRepository;
    private final TrilhaAtribuicaoRepository atribuicaoRepository;
    private final ConteudoRepository conteudoRepository;
    private final NotificacaoFactory factory;
    private final NotificacaoProperties properties;
    private final Clock clock;

    public GeradorDeNotificacoes(NotificacaoRepository notificacaoRepository,
                                 UsuarioRepository usuarioRepository,
                                 TrilhaRepository trilhaRepository,
                                 TrilhaAtribuicaoRepository atribuicaoRepository,
                                 ConteudoRepository conteudoRepository,
                                 NotificacaoFactory factory,
                                 NotificacaoProperties properties,
                                 Clock clock) {
        this.notificacaoRepository = notificacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.trilhaRepository = trilhaRepository;
        this.atribuicaoRepository = atribuicaoRepository;
        this.conteudoRepository = conteudoRepository;
        this.factory = factory;
        this.properties = properties;
        this.clock = clock;
    }

    /** TRILHA_ATRIBUIDA para cada colaborador ativo que recebeu a trilha, com o prazo dele. */
    @Transactional
    public int aoAtribuir(TrilhaAtribuidaEvent evento) {
        Trilha trilha = trilhaRepository.findByIdAndTenantId(evento.trilhaId(), evento.tenantId()).orElse(null);
        if (trilha == null) {
            return 0;
        }
        int geradas = 0;
        for (UUID usuarioId : evento.usuarioIds()) {
            TrilhaAtribuicao atribuicao = atribuicaoRepository
                    .findByTrilhaIdAndTenantIdAndUsuarioId(trilha.getId(), evento.tenantId(), usuarioId)
                    .orElse(null);
            if (atribuicao != null && atribuicao.getUsuario().isAtivo()) {
                notificacaoRepository.save(factory.trilhaAtribuida(atribuicao.getUsuario(), trilha,
                        atribuicao.getPrazo()));
                geradas++;
            }
        }
        return geradas;
    }

    /** PRAZO_VENCIDO para o colaborador, uma vez por atribuição. */
    @Transactional
    public int aoAtrasar(AtribuicaoAtrasadaEvent evento) {
        TrilhaAtribuicao atribuicao = atribuicaoRepository.findById(evento.atribuicaoId())
                .filter(encontrada -> encontrada.getTenant().getId().equals(evento.tenantId()))
                .orElse(null);
        if (atribuicao == null || !atribuicao.getUsuario().isAtivo() || atribuicao.getPrazo() == null
                || jaAvisou(atribuicao.getUsuario(), TipoNotificacao.PRAZO_VENCIDO, atribuicao.getId())) {
            return 0;
        }
        notificacaoRepository.save(factory.prazoVencido(atribuicao));
        return 1;
    }

    /**
     * TRILHA_CONCLUIDA para os gestores ativos da área de quem concluiu
     * (combinado com o grupo: a área não tem "o" gestor; são os usuários
     * GESTOR dela). Colaborador sem área: ninguém recebe.
     */
    @Transactional
    public int aoConcluir(TrilhaConcluidaEvent evento) {
        Usuario colaborador = usuarioRepository.findById(evento.usuarioId())
                .filter(encontrado -> encontrado.getTenant().getId().equals(evento.tenantId()))
                .orElse(null);
        Trilha trilha = trilhaRepository.findByIdAndTenantId(evento.trilhaId(), evento.tenantId()).orElse(null);
        if (colaborador == null || trilha == null || colaborador.getArea() == null) {
            return 0;
        }
        int geradas = 0;
        for (Usuario gestor : usuarioRepository.findByTenantIdAndAreaIdAndPapelAndAtivoTrue(
                evento.tenantId(), colaborador.getArea().getId(), Perfil.GESTOR)) {
            if (!gestor.getId().equals(colaborador.getId())) {
                notificacaoRepository.save(factory.trilhaConcluida(gestor, colaborador, trilha));
                geradas++;
            }
        }
        return geradas;
    }

    /**
     * CONTEUDO_PUBLICADO para os colaboradores ativos da área do conteúdo;
     * conteúdo sem área vale para a empresa toda, e todos os colaboradores
     * ativos recebem (combinado com o grupo). Quem publicou não recebe.
     */
    @Transactional
    public int aoPublicar(ConteudoPublicadoEvent evento) {
        Conteudo conteudo = conteudoRepository.findByIdAndTenantId(evento.conteudoId(), evento.tenantId())
                .filter(Conteudo::isPublicado)
                .orElse(null);
        // Restrito à gestão: os colaboradores nem sabem que existe.
        if (conteudo == null || conteudo.isApenasAdmin()) {
            return 0;
        }
        List<Usuario> destinatarios = conteudo.getArea() == null
                ? usuarioRepository.findByTenantIdAndPapelAndAtivoTrue(evento.tenantId(), Perfil.COLABORADOR)
                : usuarioRepository.findByTenantIdAndAreaIdAndPapelAndAtivoTrue(
                        evento.tenantId(), conteudo.getArea().getId(), Perfil.COLABORADOR);
        int geradas = 0;
        for (Usuario destinatario : destinatarios) {
            if (!destinatario.getId().equals(evento.publicadoPor())) {
                notificacaoRepository.save(factory.conteudoPublicado(destinatario, conteudo));
                geradas++;
            }
        }
        return geradas;
    }

    /**
     * TRILHA_INATIVADA para quem tinha a trilha atribuída e ainda não tinha
     * concluído (combinado com o grupo: quem concluiu não perde nada).
     */
    @Transactional
    public int aoInativar(TrilhaInativadaEvent evento) {
        Trilha trilha = trilhaRepository.findByIdAndTenantId(evento.trilhaId(), evento.tenantId()).orElse(null);
        if (trilha == null) {
            return 0;
        }
        int geradas = 0;
        for (TrilhaAtribuicao atribuicao : atribuicaoRepository.findByTrilhaIdAndTenantIdAndStatusNot(
                trilha.getId(), evento.tenantId(), StatusAtribuicao.CONCLUIDA)) {
            if (atribuicao.getUsuario().isAtivo()) {
                notificacaoRepository.save(factory.trilhaInativada(atribuicao.getUsuario(), trilha));
                geradas++;
            }
        }
        return geradas;
    }

    /**
     * PRAZO_PROXIMO para as atribuições em aberto que vencem entre hoje e
     * daqui a {@code diasAntesDoPrazo} dias, de trilha ativa e colaborador
     * ativo. Atribuição que já recebeu o aviso fica de fora: rodar de novo no
     * mesmo dia não repete nada (e o índice único do banco garante, mesmo com
     * duas instâncias). Se o prazo mudar depois do aviso, não é enviado outro
     * aviso para a mesma atribuição.
     */
    @Transactional
    public int avisarPrazosProximos() {
        LocalDate hoje = LocalDate.now(clock);
        List<TrilhaAtribuicao> candidatas = atribuicaoRepository.findByPrazoBetweenAndStatusIn(
                hoje, hoje.plusDays(properties.diasAntesDoPrazo()),
                List.of(StatusAtribuicao.NAO_INICIADA, StatusAtribuicao.EM_ANDAMENTO));
        if (candidatas.isEmpty()) {
            return 0;
        }
        Set<UUID> jaAvisadas = new HashSet<>(notificacaoRepository.referenciasJaAvisadas(
                TipoNotificacao.PRAZO_PROXIMO,
                candidatas.stream().map(TrilhaAtribuicao::getId).toList()));
        int geradas = 0;
        for (TrilhaAtribuicao atribuicao : candidatas) {
            if (!jaAvisadas.contains(atribuicao.getId())
                    && atribuicao.getUsuario().isAtivo()
                    && atribuicao.getTrilha().isAtivo()) {
                var aviso = factory.prazoProximo(atribuicao, hoje);
                geradas += notificacaoRepository.inserirPrazoProximoSeAusente(
                        aviso.getTenant().getId(), aviso.getUsuario().getId(), aviso.getTipo().name(),
                        aviso.getTitulo(), aviso.getMensagem(), aviso.getLink(), aviso.getReferenciaId());
            }
        }
        return geradas;
    }

    /** Apaga as notificações lidas há mais de {@code retencaoLidas} (90 dias). */
    @Transactional
    public int apagarLidasAntigas() {
        return notificacaoRepository.apagarLidasAntesDe(OffsetDateTime.now(clock).minus(properties.retencaoLidas()));
    }

    private boolean jaAvisou(Usuario usuario, TipoNotificacao tipo, UUID referenciaId) {
        return notificacaoRepository.existsByUsuarioIdAndTipoAndReferenciaId(
                Objects.requireNonNull(usuario).getId(), tipo, referenciaId);
    }
}
