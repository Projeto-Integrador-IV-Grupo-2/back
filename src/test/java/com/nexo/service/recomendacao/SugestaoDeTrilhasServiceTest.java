package com.nexo.service.recomendacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.config.IaProperties;
import com.nexo.dto.trilha.SugestaoTrilhaResponse;
import com.nexo.dto.trilha.SugestoesDeTrilhaResponse;
import com.nexo.entity.Perfil;
import com.nexo.exception.IaIndisponivelException;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.MensagemIa;
import com.nexo.ia.RespostaIa;
import com.nexo.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Regras da sugestão de trilhas pela IA (issue #63): descarte de id
 * inventado, cache e queda para a recomendação por área.
 *
 * <p>O que vem do banco chega pronto do {@link ColetorDeDadosDaSugestao}, que
 * aqui é mock — a montagem do catálogo tem teste próprio.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SugestaoDeTrilhasServiceTest {

    private static final IaProperties PROPRIEDADES = new IaProperties(
            "fake", null, null, null, null, null, null, null, null, null, null,
            new IaProperties.Sugestao(5, 60, Duration.ofMinutes(15)));

    @Mock
    private ColetorDeDadosDaSugestao coletor;

    @Mock
    private ClienteIa clienteIa;

    private SugestaoDeTrilhasService service;
    private CacheDeSugestoes cache;
    private UsuarioAutenticado usuario;
    private TrilhaCandidata atendimento;
    private TrilhaCandidata lgpd;
    private UUID areaSuporte;

    @BeforeEach
    void prepara() {
        cache = new CacheDeSugestoes(Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC),
                PROPRIEDADES);
        EstrategiaRecomendacaoFactory factory = new EstrategiaRecomendacaoFactory(List.of(
                new RecomendacaoPorArea(),
                new RecomendacaoPorIa(clienteIa, new ObjectMapper(), cache)));
        service = new SugestaoDeTrilhasService(coletor, factory, cache, PROPRIEDADES);

        areaSuporte = UUID.randomUUID();
        usuario = new UsuarioAutenticado(UUID.randomUUID(), UUID.randomUUID(), "Pessoa", "p@nexo.com", "{bcrypt}x",
                Perfil.COLABORADOR, true);
        atendimento = new TrilhaCandidata(UUID.randomUUID(), "Atendimento ao Cliente", areaSuporte, "Suporte",
                "Resolver conflitos");
        lgpd = new TrilhaCandidata(UUID.randomUUID(), "Fundamentos de LGPD", null, null, null);
        when(coletor.coletar(usuario)).thenReturn(dadosCom(List.of(atendimento, lgpd)));
    }

    @Test
    void sugereSoTrilhasDoCatalogoEDescartaIdInventado() {
        respondeComIa("""
                {"sugestoes":[
                  {"id":"%s","motivo":"Você atende clientes todo dia."},
                  {"id":"%s","motivo":"Trilha que o modelo inventou."}
                ]}""".formatted(atendimento.id(), UUID.randomUUID()));

        SugestoesDeTrilhaResponse resposta = service.sugerir(usuario);

        assertThat(resposta.geradoPorIa()).isTrue();
        assertThat(resposta.aviso()).isNull();
        assertThat(resposta.sugestoes()).extracting(SugestaoTrilhaResponse::id).containsExactly(atendimento.id());
        assertThat(resposta.sugestoes().get(0).motivo()).isEqualTo("Você atende clientes todo dia.");
        assertThat(resposta.sugestoes().get(0).area().nome()).isEqualTo("Suporte");
    }

    @Test
    void cadaSugestaoVemComMotivoMesmoQuandoOModeloNaoEscreveUm() {
        respondeComIa("{\"sugestoes\":[{\"id\":\"%s\"}]}".formatted(lgpd.id()));

        assertThat(service.sugerir(usuario).sugestoes()).singleElement()
                .extracting(SugestaoTrilhaResponse::motivo)
                .isEqualTo(RecomendacaoPorIa.MOTIVO_PADRAO);
    }

    @Test
    void comIaForaDoArCaiNaRecomendacaoPorAreaEAvisa() {
        when(clienteIa.gerarResposta(anyString(), anyList()))
                .thenThrow(new IaIndisponivelException("provedor fora do ar"));

        SugestoesDeTrilhaResponse resposta = service.sugerir(usuario);

        assertThat(resposta.geradoPorIa()).isFalse();
        assertThat(resposta.aviso()).isEqualTo(SugestoesDeTrilhaResponse.AVISO_SEM_IA);
        // Área do usuário primeiro.
        assertThat(resposta.sugestoes()).extracting(SugestaoTrilhaResponse::id)
                .containsExactly(atendimento.id(), lgpd.id());
        assertThat(resposta.sugestoes().get(0).motivo()).isEqualTo(RecomendacaoPorArea.MOTIVO_DA_AREA);
        assertThat(resposta.sugestoes().get(1).motivo()).isEqualTo(RecomendacaoPorArea.MOTIVO_GERAL);
    }

    @Test
    void respostaIlegivelDoModeloTambemCaiNaRecomendacaoPorArea() {
        respondeComIa("desculpe, não consegui responder");

        SugestoesDeTrilhaResponse resposta = service.sugerir(usuario);

        assertThat(resposta.geradoPorIa()).isFalse();
        assertThat(resposta.sugestoes()).isNotEmpty();
    }

    @Test
    void doisAcessosSeguidosChamamOModeloUmaVezSo() {
        respondeComIa("{\"sugestoes\":[{\"id\":\"%s\",\"motivo\":\"ok\"}]}".formatted(atendimento.id()));

        SugestoesDeTrilhaResponse primeira = service.sugerir(usuario);
        SugestoesDeTrilhaResponse segunda = service.sugerir(usuario);

        assertThat(segunda).isEqualTo(primeira);
        verify(clienteIa, times(1)).gerarResposta(anyString(), anyList());
        // Nem o banco é lido de novo: o cache responde antes.
        verify(coletor, times(1)).coletar(usuario);
    }

    @Test
    void depoisDeInvalidarOCacheOModeloEhChamadoDeNovo() {
        respondeComIa("{\"sugestoes\":[{\"id\":\"%s\",\"motivo\":\"ok\"}]}".formatted(atendimento.id()));

        service.sugerir(usuario);
        cache.invalidar(usuario.getTenantId(), usuario.getId());
        service.sugerir(usuario);

        verify(clienteIa, times(2)).gerarResposta(anyString(), anyList());
    }

    @Test
    void semTrilhaCandidataNaoChamaOModelo() {
        when(coletor.coletar(usuario)).thenReturn(dadosCom(List.of()));

        assertThat(service.sugerir(usuario).sugestoes()).isEmpty();
        verify(clienteIa, never()).gerarResposta(anyString(), anyList());
    }

    @Test
    void nenhumDadoPessoalVaiParaOModelo() {
        respondeComIa("{\"sugestoes\":[]}");

        service.sugerir(usuario);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MensagemIa>> mensagens = ArgumentCaptor.forClass(List.class);
        verify(clienteIa).gerarResposta(anyString(), mensagens.capture());
        String turno = mensagens.getValue().get(0).texto();
        assertThat(turno).contains("Área: Suporte").contains("Cargo: Analista")
                .contains("Trilhas já concluídas: Onboarding");
        assertThat(turno).doesNotContain(usuario.getEmail()).doesNotContain("Pessoa")
                .doesNotContain(usuario.getId().toString());
    }

    private DadosParaSugestao dadosCom(List<TrilhaCandidata> catalogo) {
        return new DadosParaSugestao(
                new EstrategiaRecomendacao.Contexto(usuario.getTenantId(), usuario.getId(), areaSuporte),
                new PerfilDoUsuario("Suporte", "Analista", List.of("Onboarding"), List.of(), List.of()),
                catalogo);
    }

    private void respondeComIa(String texto) {
        when(clienteIa.gerarResposta(anyString(), anyList())).thenReturn(new RespostaIa(texto, 0, 0));
    }
}
