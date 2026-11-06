package com.nexo.service.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexo.entity.Area;
import com.nexo.entity.Conteudo;
import com.nexo.entity.Notificacao;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoConteudo;
import com.nexo.entity.TipoNotificacao;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NotificacaoFactoryTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 5);

    private final NotificacaoFactory factory = new NotificacaoFactory();
    private Usuario ana;
    private Trilha trilha;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        ana = usuario(tenant, "Ana Souza");
        trilha = new Trilha();
        trilha.setTenant(tenant);
        trilha.setNome("Segurança da informação");
        ReflectionTestUtils.setField(trilha, "id", UUID.randomUUID());
    }

    @Test
    void trilhaAtribuidaLevaAoDetalheEMostraOPrazo() {
        Notificacao comPrazo = factory.trilhaAtribuida(ana, trilha, LocalDate.of(2026, 12, 1));
        Notificacao semPrazo = factory.trilhaAtribuida(ana, trilha, null);

        assertThat(comPrazo.getTipo()).isEqualTo(TipoNotificacao.TRILHA_ATRIBUIDA);
        assertThat(comPrazo.getUsuario()).isEqualTo(ana);
        assertThat(comPrazo.getTenant()).isEqualTo(ana.getTenant());
        assertThat(comPrazo.getTitulo()).isEqualTo("Nova trilha para você");
        assertThat(comPrazo.getMensagem())
                .isEqualTo("A trilha “Segurança da informação” foi atribuída a você, com prazo até 01/12/2026.");
        assertThat(comPrazo.getLink()).isEqualTo("/trilhas/" + trilha.getId());
        assertThat(semPrazo.getMensagem()).endsWith("foi atribuída a você.");
    }

    @Test
    void prazoProximoDizQuantoFaltaEApontaParaAAtribuicao() {
        TrilhaAtribuicao atribuicao = atribuicao(HOJE.plusDays(3));

        Notificacao aviso = factory.prazoProximo(atribuicao, HOJE);

        assertThat(aviso.getTipo()).isEqualTo(TipoNotificacao.PRAZO_PROXIMO);
        assertThat(aviso.getMensagem()).isEqualTo("A trilha “Segurança da informação” vence em 3 dias (08/11/2026).");
        assertThat(aviso.getReferenciaId()).isEqualTo(atribuicao.getId());
        assertThat(factory.prazoProximo(atribuicao(HOJE.plusDays(1)), HOJE).getMensagem()).contains("vence amanhã");
        assertThat(factory.prazoProximo(atribuicao(HOJE), HOJE).getMensagem()).contains("vence hoje");
    }

    @Test
    void prazoVencidoLevaAoDetalheDaTrilha() {
        TrilhaAtribuicao atribuicao = atribuicao(HOJE.minusDays(1));

        Notificacao aviso = factory.prazoVencido(atribuicao);

        assertThat(aviso.getTipo()).isEqualTo(TipoNotificacao.PRAZO_VENCIDO);
        assertThat(aviso.getMensagem()).startsWith("O prazo da trilha “Segurança da informação” venceu em 04/11/2026.");
        assertThat(aviso.getLink()).isEqualTo("/trilhas/" + trilha.getId());
    }

    @Test
    void trilhaConcluidaVaiParaOAcompanhamentoDoGestor() {
        Usuario gestor = usuario(ana.getTenant(), "Bruno Lima");

        Notificacao aviso = factory.trilhaConcluida(gestor, ana, trilha);

        assertThat(aviso.getUsuario()).isEqualTo(gestor);
        assertThat(aviso.getMensagem()).isEqualTo("Ana Souza concluiu a trilha “Segurança da informação”.");
        assertThat(aviso.getLink()).isEqualTo("/acompanhamento");
    }

    @Test
    void conteudoPublicadoAbreODocumentoOuOCatalogo() {
        Conteudo documento = conteudo(TipoConteudo.DOCUMENTO, true);
        Conteudo video = conteudo(TipoConteudo.VIDEO, false);

        Notificacao doDocumento = factory.conteudoPublicado(ana, documento);
        Notificacao doVideo = factory.conteudoPublicado(ana, video);

        assertThat(doDocumento.getLink()).isEqualTo("/conhecimento/" + documento.getId());
        assertThat(doDocumento.getTitulo()).isEqualTo("Novo conteúdo na sua área");
        assertThat(doDocumento.getMensagem()).isEqualTo("“Manual” foi publicado.");
        assertThat(doVideo.getLink()).isEqualTo("/conteudos");
        assertThat(doVideo.getTitulo()).isEqualTo("Novo conteúdo publicado");
    }

    @Test
    void trilhaInativadaLevaParaAListaDeTrilhas() {
        Notificacao aviso = factory.trilhaInativada(ana, trilha);

        assertThat(aviso.getTipo()).isEqualTo(TipoNotificacao.TRILHA_INATIVADA);
        assertThat(aviso.getLink()).isEqualTo("/trilhas");
    }

    @Test
    void textoLongoECortadoNoTamanhoDaColuna() {
        trilha.setNome("x".repeat(600));

        Notificacao aviso = factory.trilhaAtribuida(ana, trilha, null);

        assertThat(aviso.getMensagem()).hasSize(Notificacao.MENSAGEM_MAXIMA).endsWith("…");
    }

    private TrilhaAtribuicao atribuicao(LocalDate prazo) {
        TrilhaAtribuicao atribuicao = new TrilhaAtribuicao();
        atribuicao.setTenant(ana.getTenant());
        atribuicao.setTrilha(trilha);
        atribuicao.setUsuario(ana);
        atribuicao.setPrazo(prazo);
        ReflectionTestUtils.setField(atribuicao, "id", UUID.randomUUID());
        return atribuicao;
    }

    private Conteudo conteudo(TipoConteudo tipo, boolean comArea) {
        Conteudo conteudo = new Conteudo();
        conteudo.setTenant(ana.getTenant());
        conteudo.setTitulo("Manual");
        conteudo.setTipo(tipo);
        if (comArea) {
            Area area = new Area();
            ReflectionTestUtils.setField(area, "id", UUID.randomUUID());
            conteudo.setArea(area);
        }
        ReflectionTestUtils.setField(conteudo, "id", UUID.randomUUID());
        return conteudo;
    }

    private static Usuario usuario(Tenant tenant, String nome) {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto(nome);
        usuario.setPapel(Perfil.COLABORADOR);
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        return usuario;
    }
}
