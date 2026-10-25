package com.nexo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.entity.ChatMensagem;
import com.nexo.entity.ChatSessao;
import com.nexo.entity.PapelMensagem;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.exception.IaIndisponivelException;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.ClienteIaFake;
import com.nexo.repository.ChatMensagemRepository;
import com.nexo.repository.ChatSessaoRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.service.assistente.ConsultaAssistenteService;
import com.nexo.service.indexacao.IndexacaoService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ponta a ponta da issue #60 com PostgreSQL + pgvector reais e o
 * {@link ClienteIaFake} (envolvido por um spy, para simular o provedor fora do ar).
 *
 * <p>O teste é {@code @Transactional}, então a indexação assíncrona (que só
 * roda depois do commit) não dispara: os conteúdos são indexados chamando o
 * {@link IndexacaoService} direto, dentro da mesma transação.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssistenteControllerTest {

    private static final String SENHA = "senha-de-teste-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IndexacaoService indexacaoService;

    @Autowired
    private ChatSessaoRepository chatSessaoRepository;

    @Autowired
    private ChatMensagemRepository chatMensagemRepository;

    @MockitoSpyBean
    private ClienteIa clienteIa;

    private Tenant tenant;
    private String tokenAdmin;
    private Usuario colaborador;
    private String tokenColaborador;

    @BeforeEach
    void prepara() throws Exception {
        tenant = criaTenant();
        tokenAdmin = tokenDe(criaUsuario(tenant, Perfil.ADMIN));
        colaborador = criaUsuario(tenant, Perfil.COLABORADOR);
        tokenColaborador = tokenDe(colaborador);
    }

    @Test
    void criaSessaoDoContextoConhecimentoPorPadrao() throws Exception {
        mockMvc.perform(comToken(post("/api/assistente/sessoes"), tokenColaborador))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://localhost/api/assistente/sessoes/")))
                .andExpect(jsonPath("$.contexto").value("CONHECIMENTO"))
                .andExpect(jsonPath("$.titulo").doesNotExist());
    }

    @Test
    void respondeComFontesPublicadasDoTenantEGravaATroca() throws Exception {
        UUID ferias = publicaEIndexa("Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        criaRascunho("Férias secretas", "Rascunho: colaborador terá quarenta dias de férias.");
        UUID sessaoId = criaSessao(tokenColaborador);

        mockMvc.perform(pergunta(sessaoId, "Quantos dias de férias o colaborador tem?", tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagemId").isNotEmpty())
                .andExpect(jsonPath("$.resposta", startsWith(ClienteIaFake.PREFIXO_RESPOSTA)))
                .andExpect(jsonPath("$.fontes", hasSize(1)))
                .andExpect(jsonPath("$.fontes[0].conteudoId").value(ferias.toString()))
                .andExpect(jsonPath("$.fontes[0].titulo").value("Política de férias"));

        List<ChatMensagem> mensagens = chatMensagemRepository.findBySessaoIdOrderByCriadoEm(sessaoId);
        assertThat(mensagens).extracting(ChatMensagem::getPapel)
                .containsExactly(PapelMensagem.USUARIO, PapelMensagem.ASSISTENTE);
        assertThat(mensagens.get(1).getFontes()).isNotEmpty()
                .allSatisfy(fonte -> assertThat(fonte.conteudoId()).isEqualTo(ferias));
        ChatSessao sessao = chatSessaoRepository.findById(sessaoId).orElseThrow();
        assertThat(sessao.getTitulo()).isEqualTo("Quantos dias de férias o colaborador tem?");
    }

    @Test
    void perguntaSemDocumentoRelevanteRecebeNaoEncontreiSemFonte() throws Exception {
        publicaEIndexa("Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        UUID sessaoId = criaSessao(tokenColaborador);

        mockMvc.perform(pergunta(sessaoId, "Qual a senha do wifi da recepção?", tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resposta").value(ConsultaAssistenteService.RESPOSTA_NAO_ENCONTREI))
                .andExpect(jsonPath("$.fontes", hasSize(0)));
    }

    @Test
    void naoUsaDocumentoDeOutroTenant() throws Exception {
        Tenant outro = criaTenant();
        String tokenOutro = tokenDe(criaUsuario(outro, Perfil.ADMIN));
        publicaEIndexa(tokenOutro, "Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        UUID sessaoId = criaSessao(tokenColaborador);

        mockMvc.perform(pergunta(sessaoId, "Quantos dias de férias o colaborador tem?", tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resposta").value(ConsultaAssistenteService.RESPOSTA_NAO_ENCONTREI))
                .andExpect(jsonPath("$.fontes", hasSize(0)));
    }

    @Test
    void sessaoDeOutroUsuarioResponde404MesmoParaOAdmin() throws Exception {
        UUID sessaoDoColaborador = criaSessao(tokenColaborador);

        mockMvc.perform(pergunta(sessaoDoColaborador, "Oi?", tokenAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(pergunta(UUID.randomUUID(), "Oi?", tokenColaborador))
                .andExpect(status().isNotFound());
    }

    @Test
    void provedorForaDoArResponde503ENaoGravaAPergunta() throws Exception {
        publicaEIndexa("Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        UUID sessaoId = criaSessao(tokenColaborador);
        doThrow(new IaIndisponivelException("O assistente de IA está indisponível no momento. Tente novamente em instantes."))
                .when(clienteIa).gerarResposta(anyString(), anyList());

        mockMvc.perform(pergunta(sessaoId, "Quantos dias de férias o colaborador tem?", tokenColaborador))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "O assistente de IA está indisponível no momento. Tente novamente em instantes."));

        assertThat(chatMensagemRepository.findBySessaoIdOrderByCriadoEm(sessaoId)).isEmpty();
    }

    @Test
    void excessoDePerguntasResponde429() throws Exception {
        UUID sessaoId = criaSessao(tokenColaborador);
        OffsetDateTime agora = OffsetDateTime.now();
        for (int i = 0; i < 10; i++) {
            chatMensagemRepository.save(ChatMensagem.doUsuario(sessaoId, "pergunta " + i, agora.minusSeconds(i)));
        }

        mockMvc.perform(pergunta(sessaoId, "Mais uma?", tokenColaborador))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
    }

    @Test
    void perguntaVaziaOuGrandeDemaisResponde400() throws Exception {
        UUID sessaoId = criaSessao(tokenColaborador);

        mockMvc.perform(pergunta(sessaoId, "   ", tokenColaborador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("pergunta"));
        mockMvc.perform(pergunta(sessaoId, "a".repeat(2001), tokenColaborador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("pergunta"));
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(post("/api/assistente/sessoes")).andExpect(status().isUnauthorized());
    }

    private UUID publicaEIndexa(String titulo, String corpo) throws Exception {
        return publicaEIndexa(tokenAdmin, titulo, corpo);
    }

    private UUID publicaEIndexa(String token, String titulo, String corpo) throws Exception {
        UUID id = criaConteudo(token, titulo, corpo);
        mockMvc.perform(comToken(patch("/api/conteudos/{id}/publicar", id), token)).andExpect(status().isOk());
        indexacaoService.indexar(id);
        return id;
    }

    private UUID criaRascunho(String titulo, String corpo) throws Exception {
        UUID id = criaConteudo(tokenAdmin, titulo, corpo);
        indexacaoService.indexar(id);
        return id;
    }

    private UUID criaConteudo(String token, String titulo, String corpo) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/conteudos"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", titulo, "tipo", "TEXTO", "corpo", corpo))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private UUID criaSessao(String token) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/assistente/sessoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("contexto", "CONHECIMENTO"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private MockHttpServletRequestBuilder pergunta(UUID sessaoId, String texto, String token) throws Exception {
        return comToken(post("/api/assistente/sessoes/{id}/mensagens", sessaoId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("pergunta", texto)));
    }

    private Tenant criaTenant() {
        Tenant novo = new Tenant();
        novo.setNome("Empresa " + UUID.randomUUID());
        novo.setSubdominio("empresa-" + UUID.randomUUID());
        return tenantRepository.saveAndFlush(novo);
    }

    private Usuario criaUsuario(Tenant dono, Perfil papel) {
        Usuario usuario = new Usuario();
        usuario.setTenant(dono);
        usuario.setNomeCompleto("Usuário de Teste");
        usuario.setEmail("assistente-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPapel(papel);
        usuario.setAtivo(true);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private String tokenDe(Usuario usuario) throws Exception {
        String corpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(usuario.getEmail(), SENHA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private static MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder requisicao, String token) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String json(Object corpo) throws Exception {
        return objectMapper.writeValueAsString(corpo);
    }
}
