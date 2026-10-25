package com.nexo.service.recomendacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.exception.IaIndisponivelException;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.MensagemIa;
import com.nexo.ia.RespostaIa;
import com.nexo.repository.PdiItemRepository;
import com.nexo.repository.PdiRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Regras da sugestão de trilhas pela IA (issue #63): catálogo, descarte de id
 * inventado, cache e queda para a recomendação por área.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SugestaoDeTrilhasServiceTest {

    private static final IaProperties PROPRIEDADES = new IaProperties(
            "fake", null, null, null, null, null, null, null, null, null, null,
            new IaProperties.Sugestao(5, 60, Duration.ofMinutes(15)));

    @Mock
    private TrilhaRepository trilhaRepository;

    @Mock
    private TrilhaAtribuicaoRepository atribuicaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PdiRepository pdiRepository;

    @Mock
    private PdiItemRepository pdiItemRepository;

    @Mock
    private ClienteIa clienteIa;

    private SugestaoDeTrilhasService service;
    private CacheDeSugestoes cache;
    private UsuarioAutenticado usuario;
    private Tenant tenant;
    private Area suporte;
    private Trilha atendimento;
    private Trilha lgpd;
    private Trilha concluida;

    @BeforeEach
    void prepara() {
        cache = new CacheDeSugestoes(Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC),
                PROPRIEDADES);
        RecomendacaoPorIa porIa = new RecomendacaoPorIa(usuarioRepository, atribuicaoRepository, pdiRepository,
                pdiItemRepository, clienteIa, new ObjectMapper(), cache, PROPRIEDADES);
        EstrategiaRecomendacaoFactory factory =
                new EstrategiaRecomendacaoFactory(List.of(new RecomendacaoPorArea(), porIa));
        service = new SugestaoDeTrilhasService(trilhaRepository, atribuicaoRepository, usuarioRepository,
                factory, cache, PROPRIEDADES);

        tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        suporte = area("Suporte");
        usuario = new UsuarioAutenticado(UUID.randomUUID(), tenant.getId(), "Pessoa", "p@nexo.com", "{bcrypt}x",
                Perfil.COLABORADOR, true);

        atendimento = trilha("Atendimento ao Cliente", suporte);
        lgpd = trilha("Fundamentos de LGPD", null);
        concluida = trilha("Onboarding", suporte);

        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuarioComArea()));
        when(trilhaRepository.findByTenantIdAndAtivoTrueOrderByNome(tenant.getId()))
                .thenReturn(List.of(atendimento, lgpd, concluida));
        when(atribuicaoRepository.findByTenantIdAndUsuarioId(tenant.getId(), usuario.getId()))
                .thenReturn(List.of(atribuicao(concluida, StatusAtribuicao.CONCLUIDA)));
        when(pdiRepository.findFirstByTenantIdAndColaboradorIdAndStatusOrderByInicioDescCriadoEmDesc(
                any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void sugereSoTrilhasDoCatalogoEDescartaIdInventado() {
        respondeComIa("""
                {"sugestoes":[
                  {"id":"%s","motivo":"Você atende clientes todo dia."},
                  {"id":"%s","motivo":"Trilha que não existe."},
                  {"id":"%s","motivo":"Você já concluiu esta."}
                ]}""".formatted(atendimento.getId(), UUID.randomUUID(), concluida.getId()));

        SugestoesDeTrilhaResponse resposta = service.sugerir(usuario);

        assertThat(resposta.geradoPorIa()).isTrue();
        assertThat(resposta.aviso()).isNull();
        assertThat(resposta.sugestoes()).extracting(SugestaoTrilhaResponse::id).containsExactly(atendimento.getId());
        assertThat(resposta.sugestoes().get(0).motivo()).isEqualTo("Você atende clientes todo dia.");
    }

    @Test
    void aTrilhaJaConcluidaNaoEntraNoPromptEnviadoAoModelo() {
        respondeComIa("{\"sugestoes\":[]}");

        service.sugerir(usuario);

        ArgumentCaptor<List<MensagemIa>> mensagens =
                ArgumentCaptor.forClass(List.class);
        verify(clienteIa).gerarResposta(anyString(), mensagens.capture());
        String turno = mensagens.getValue().get(0).texto();
        assertThat(turno).contains(atendimento.getId().toString()).contains(lgpd.getId().toString());
        assertThat(turno).doesNotContain(concluida.getId().toString());
    }

    @Test
    void cadaSugestaoVemComMotivoMesmoQuandoOModeloNaoEscreveUm() {
        respondeComIa("{\"sugestoes\":[{\"id\":\"%s\"}]}".formatted(lgpd.getId()));

        SugestoesDeTrilhaResponse resposta = service.sugerir(usuario);

        assertThat(resposta.sugestoes()).singleElement()
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
        // Área do usuário primeiro; a trilha concluída continua fora.
        assertThat(resposta.sugestoes()).extracting(SugestaoTrilhaResponse::id)
                .containsExactly(atendimento.getId(), lgpd.getId());
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
        respondeComIa("{\"sugestoes\":[{\"id\":\"%s\",\"motivo\":\"ok\"}]}".formatted(atendimento.getId()));

        SugestoesDeTrilhaResponse primeira = service.sugerir(usuario);
        SugestoesDeTrilhaResponse segunda = service.sugerir(usuario);

        assertThat(segunda).isEqualTo(primeira);
        verify(clienteIa, times(1)).gerarResposta(anyString(), anyList());
    }

    @Test
    void depoisDeInvalidarOCacheOModeloEhChamadoDeNovo() {
        respondeComIa("{\"sugestoes\":[{\"id\":\"%s\",\"motivo\":\"ok\"}]}".formatted(atendimento.getId()));

        service.sugerir(usuario);
        cache.invalidar(tenant.getId(), usuario.getId());
        service.sugerir(usuario);

        verify(clienteIa, times(2)).gerarResposta(anyString(), anyList());
    }

    @Test
    void semTrilhaCandidataNaoChamaOModelo() {
        when(trilhaRepository.findByTenantIdAndAtivoTrueOrderByNome(tenant.getId())).thenReturn(List.of());

        SugestoesDeTrilhaResponse resposta = service.sugerir(usuario);

        assertThat(resposta.sugestoes()).isEmpty();
        verify(clienteIa, never()).gerarResposta(anyString(), anyList());
    }

    @Test
    void nenhumDadoPessoalVaiParaOModelo() {
        respondeComIa("{\"sugestoes\":[]}");

        service.sugerir(usuario);

        ArgumentCaptor<List<MensagemIa>> mensagens =
                ArgumentCaptor.forClass(List.class);
        verify(clienteIa).gerarResposta(anyString(), mensagens.capture());
        String turno = mensagens.getValue().get(0).texto();
        assertThat(turno).contains("Área: Suporte").contains("Cargo: não informado");
        assertThat(turno).doesNotContain(usuario.getEmail()).doesNotContain("Pessoa")
                .doesNotContain(usuario.getId().toString());
    }

    private void respondeComIa(String texto) {
        when(clienteIa.gerarResposta(anyString(), anyList())).thenReturn(new RespostaIa(texto, 0, 0));
    }

    private Usuario usuarioComArea() {
        Usuario entidade = new Usuario();
        ReflectionTestUtils.setField(entidade, "id", usuario.getId());
        entidade.setTenant(tenant);
        entidade.setNomeCompleto("Pessoa");
        entidade.setEmail(usuario.getEmail());
        entidade.setArea(suporte);
        return entidade;
    }

    private Area area(String nome) {
        Area nova = new Area();
        ReflectionTestUtils.setField(nova, "id", UUID.randomUUID());
        nova.setTenant(tenant);
        nova.setNome(nome);
        return nova;
    }

    private Trilha trilha(String nome, Area area) {
        Trilha nova = new Trilha();
        ReflectionTestUtils.setField(nova, "id", UUID.randomUUID());
        nova.setTenant(tenant);
        nova.setNome(nome);
        nova.setArea(area);
        return nova;
    }

    private TrilhaAtribuicao atribuicao(Trilha trilha, StatusAtribuicao status) {
        TrilhaAtribuicao nova = new TrilhaAtribuicao();
        nova.setTenant(tenant);
        nova.setTrilha(trilha);
        nova.setStatus(status);
        return nova;
    }

}
