package com.nexo.service.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.config.NotificacaoProperties;
import com.nexo.entity.Area;
import com.nexo.entity.Conteudo;
import com.nexo.entity.Notificacao;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.StatusConteudo;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoConteudo;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GeradorDeNotificacoesTest {

    private static final Instant AGORA = Instant.parse("2026-11-05T12:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 5);

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TrilhaRepository trilhaRepository;

    @Mock
    private TrilhaAtribuicaoRepository atribuicaoRepository;

    @Mock
    private ConteudoRepository conteudoRepository;

    private GeradorDeNotificacoes gerador;
    private Tenant tenant;
    private Area area;
    private Trilha trilha;

    @BeforeEach
    void setUp() {
        gerador = new GeradorDeNotificacoes(notificacaoRepository, usuarioRepository, trilhaRepository,
                atribuicaoRepository, conteudoRepository, new NotificacaoFactory(),
                new NotificacaoProperties(3, Duration.ofDays(90)), Clock.fixed(AGORA, ZoneOffset.UTC));
        tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        area = new Area();
        ReflectionTestUtils.setField(area, "id", UUID.randomUUID());
        trilha = new Trilha();
        trilha.setTenant(tenant);
        trilha.setNome("LGPD");
        ReflectionTestUtils.setField(trilha, "id", UUID.randomUUID());
    }

    @Test
    void atribuirAvisaCadaColaboradorAtivoComOPrazoDele() {
        Usuario ana = usuario(Perfil.COLABORADOR, true);
        Usuario inativo = usuario(Perfil.COLABORADOR, false);
        when(trilhaRepository.findByIdAndTenantId(trilha.getId(), tenant.getId())).thenReturn(Optional.of(trilha));
        when(atribuicaoRepository.findByTrilhaIdAndTenantIdAndUsuarioId(trilha.getId(), tenant.getId(), ana.getId()))
                .thenReturn(Optional.of(atribuicao(ana, HOJE.plusDays(10), StatusAtribuicao.NAO_INICIADA)));
        when(atribuicaoRepository.findByTrilhaIdAndTenantIdAndUsuarioId(
                trilha.getId(), tenant.getId(), inativo.getId()))
                .thenReturn(Optional.of(atribuicao(inativo, null, StatusAtribuicao.NAO_INICIADA)));

        int geradas = gerador.aoAtribuir(new TrilhaAtribuidaEvent(trilha.getId(), tenant.getId(),
                List.of(ana.getId(), inativo.getId()), UUID.randomUUID()));

        assertThat(geradas).isEqualTo(1);
        Notificacao aviso = salva();
        assertThat(aviso.getUsuario()).isEqualTo(ana);
        assertThat(aviso.getMensagem()).contains("prazo até 15/11/2026");
    }

    @Test
    void concluirAvisaOsGestoresDaAreaMenosQuemConcluiu() {
        Usuario ana = usuario(Perfil.COLABORADOR, true);
        ana.setArea(area);
        Usuario gestor = usuario(Perfil.GESTOR, true);
        when(usuarioRepository.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(trilhaRepository.findByIdAndTenantId(trilha.getId(), tenant.getId())).thenReturn(Optional.of(trilha));
        when(usuarioRepository.findByTenantIdAndAreaIdAndPapelAndAtivoTrue(tenant.getId(), area.getId(),
                Perfil.GESTOR)).thenReturn(List.of(gestor));

        int geradas = gerador.aoConcluir(new TrilhaConcluidaEvent(tenant.getId(), trilha.getId(), ana.getId(),
                OffsetDateTime.now()));

        assertThat(geradas).isEqualTo(1);
        assertThat(salva().getUsuario()).isEqualTo(gestor);
    }

    @Test
    void colaboradorSemAreaNaoAvisaNinguem() {
        Usuario ana = usuario(Perfil.COLABORADOR, true);
        when(usuarioRepository.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(trilhaRepository.findByIdAndTenantId(trilha.getId(), tenant.getId())).thenReturn(Optional.of(trilha));

        assertThat(gerador.aoConcluir(new TrilhaConcluidaEvent(tenant.getId(), trilha.getId(), ana.getId(),
                OffsetDateTime.now()))).isZero();
        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    void conteudoDaAreaAvisaOsColaboradoresDelaMenosQuemPublicou() {
        Usuario ana = usuario(Perfil.COLABORADOR, true);
        Usuario autor = usuario(Perfil.COLABORADOR, true);
        Conteudo conteudo = conteudo(area);
        when(conteudoRepository.findByIdAndTenantId(conteudo.getId(), tenant.getId()))
                .thenReturn(Optional.of(conteudo));
        when(usuarioRepository.findByTenantIdAndAreaIdAndPapelAndAtivoTrue(tenant.getId(), area.getId(),
                Perfil.COLABORADOR)).thenReturn(List.of(ana, autor));

        int geradas = gerador.aoPublicar(new ConteudoPublicadoEvent(conteudo.getId(), tenant.getId(), area.getId(),
                autor.getId()));

        assertThat(geradas).isEqualTo(1);
        assertThat(salva().getUsuario()).isEqualTo(ana);
    }

    @Test
    void conteudoSemAreaAvisaTodosOsColaboradores() {
        Usuario ana = usuario(Perfil.COLABORADOR, true);
        Usuario bia = usuario(Perfil.COLABORADOR, true);
        Conteudo conteudo = conteudo(null);
        when(conteudoRepository.findByIdAndTenantId(conteudo.getId(), tenant.getId()))
                .thenReturn(Optional.of(conteudo));
        when(usuarioRepository.findByTenantIdAndPapelAndAtivoTrue(tenant.getId(), Perfil.COLABORADOR))
                .thenReturn(List.of(ana, bia));

        assertThat(gerador.aoPublicar(new ConteudoPublicadoEvent(conteudo.getId(), tenant.getId(), null,
                UUID.randomUUID()))).isEqualTo(2);
    }

    @Test
    void conteudoQueVoltouARascunhoNaoAvisa() {
        Conteudo conteudo = conteudo(area);
        conteudo.setStatus(StatusConteudo.RASCUNHO);
        when(conteudoRepository.findByIdAndTenantId(conteudo.getId(), tenant.getId()))
                .thenReturn(Optional.of(conteudo));

        assertThat(gerador.aoPublicar(new ConteudoPublicadoEvent(conteudo.getId(), tenant.getId(), area.getId(),
                UUID.randomUUID()))).isZero();
    }

    @Test
    void inativarAvisaSoQuemNaoConcluiu() {
        Usuario ana = usuario(Perfil.COLABORADOR, true);
        when(trilhaRepository.findByIdAndTenantId(trilha.getId(), tenant.getId())).thenReturn(Optional.of(trilha));
        when(atribuicaoRepository.findByTrilhaIdAndTenantIdAndStatusNot(trilha.getId(), tenant.getId(),
                StatusAtribuicao.CONCLUIDA))
                .thenReturn(List.of(atribuicao(ana, null, StatusAtribuicao.EM_ANDAMENTO)));

        assertThat(gerador.aoInativar(new TrilhaInativadaEvent(trilha.getId(), tenant.getId(), UUID.randomUUID())))
                .isEqualTo(1);
        assertThat(salva().getTipo()).isEqualTo(TipoNotificacao.TRILHA_INATIVADA);
    }

    @Test
    void prazoVencidoNaoRepeteParaAMesmaAtribuicao() {
        Usuario ana = usuario(Perfil.COLABORADOR, true);
        TrilhaAtribuicao atribuicao = atribuicao(ana, HOJE.minusDays(1), StatusAtribuicao.ATRASADA);
        when(atribuicaoRepository.findById(atribuicao.getId())).thenReturn(Optional.of(atribuicao));
        when(notificacaoRepository.existsByUsuarioIdAndTipoAndReferenciaId(ana.getId(),
                TipoNotificacao.PRAZO_VENCIDO, atribuicao.getId())).thenReturn(false, true);
        AtribuicaoAtrasadaEvent evento = new AtribuicaoAtrasadaEvent(atribuicao.getId(), tenant.getId(),
                trilha.getId(), ana.getId(), atribuicao.getPrazo());

        assertThat(gerador.aoAtrasar(evento)).isEqualTo(1);
        assertThat(gerador.aoAtrasar(evento)).isZero();
    }

    @Test
    void prazoProximoNaoRepeteNemAvisaInativos() {
        Usuario ana = usuario(Perfil.COLABORADOR, true);
        Usuario inativo = usuario(Perfil.COLABORADOR, false);
        TrilhaAtribuicao nova = atribuicao(ana, HOJE.plusDays(2), StatusAtribuicao.EM_ANDAMENTO);
        TrilhaAtribuicao jaAvisada = atribuicao(ana, HOJE.plusDays(1), StatusAtribuicao.NAO_INICIADA);
        TrilhaAtribuicao deInativo = atribuicao(inativo, HOJE.plusDays(1), StatusAtribuicao.NAO_INICIADA);
        when(atribuicaoRepository.findByPrazoBetweenAndStatusIn(eq(HOJE), eq(HOJE.plusDays(3)), anyCollection()))
                .thenReturn(List.of(nova, jaAvisada, deInativo));
        when(notificacaoRepository.referenciasJaAvisadas(eq(TipoNotificacao.PRAZO_PROXIMO), anyCollection()))
                .thenReturn(List.of(jaAvisada.getId()));

        assertThat(gerador.avisarPrazosProximos()).isEqualTo(1);
        Notificacao aviso = salva();
        assertThat(aviso.getReferenciaId()).isEqualTo(nova.getId());
        assertThat(aviso.getMensagem()).contains("vence em 2 dias");
    }

    @Test
    void limpezaApagaAsLidasHaMaisDe90Dias() {
        gerador.apagarLidasAntigas();

        verify(notificacaoRepository).apagarLidasAntesDe(OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC)
                .minusDays(90));
    }

    private Notificacao salva() {
        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(captor.capture());
        return captor.getValue();
    }

    private TrilhaAtribuicao atribuicao(Usuario usuario, LocalDate prazo, StatusAtribuicao status) {
        TrilhaAtribuicao atribuicao = new TrilhaAtribuicao();
        atribuicao.setTenant(tenant);
        atribuicao.setTrilha(trilha);
        atribuicao.setUsuario(usuario);
        atribuicao.setPrazo(prazo);
        atribuicao.setStatus(status);
        ReflectionTestUtils.setField(atribuicao, "id", UUID.randomUUID());
        return atribuicao;
    }

    private Conteudo conteudo(Area daArea) {
        Conteudo conteudo = new Conteudo();
        conteudo.setTenant(tenant);
        conteudo.setTitulo("Manual");
        conteudo.setTipo(TipoConteudo.DOCUMENTO);
        conteudo.setStatus(StatusConteudo.PUBLICADO);
        conteudo.setArea(daArea);
        ReflectionTestUtils.setField(conteudo, "id", UUID.randomUUID());
        return conteudo;
    }

    private Usuario usuario(Perfil papel, boolean ativo) {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto(papel.name());
        usuario.setPapel(papel);
        usuario.setAtivo(ativo);
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        return usuario;
    }
}
