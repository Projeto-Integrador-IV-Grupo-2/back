package com.nexo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.dto.assistente.SessaoResponse;
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
import jakarta.persistence.EntityManager;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

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
                .andExpect(jsonPath("$.titulo").value(SessaoResponse.TITULO_PADRAO));
    }

    @Test
    void respondeComFontesPublicadasDoTenantEGravaATroca() throws Exception {
        UUID ferias = publicaEIndexa("Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        criaRascunho("Férias secretas", "Rascunho: colaborador terá quarenta dias de férias.");
        UUID sessaoId = criaSessao(tokenColaborador);

        mockMvc.perform(pergunta(sessaoId, "Quantos dias de férias o colaborador tem?", tokenColaborador))
                .andExpect(status().isOk())
                // A troca inteira volta: sessão já atualizada, pergunta e resposta.
                .andExpect(jsonPath("$.sessao.titulo").value("Quantos dias de férias o colaborador tem?"))
                .andExpect(jsonPath("$.pergunta.papel").value("USUARIO"))
                .andExpect(jsonPath("$.pergunta.conteudo").value("Quantos dias de férias o colaborador tem?"))
                .andExpect(jsonPath("$.resposta.id").isNotEmpty())
                .andExpect(jsonPath("$.resposta.papel").value("ASSISTENTE"))
                .andExpect(jsonPath("$.resposta.conteudo", startsWith(ClienteIaFake.PREFIXO_RESPOSTA)))
                .andExpect(jsonPath("$.resposta.fontes", hasSize(1)))
                .andExpect(jsonPath("$.resposta.fontes[0]").value("Política de férias"))
                .andExpect(jsonPath("$.fontes", hasSize(1)))
                .andExpect(jsonPath("$.fontes[0].documentoId").value(ferias.toString()))
                .andExpect(jsonPath("$.fontes[0].titulo").value("Política de férias"))
                .andExpect(jsonPath("$.fontes[0].trechoId").isNotEmpty())
                .andExpect(jsonPath("$.fontes[0].trecho", containsString("Cada colaborador tem trinta dias de férias por ano.")));

        // Força round-trip real do JSONB das fontes, sem depender do objeto ainda gerenciado.
        entityManager.flush();
        entityManager.clear();
        List<ChatMensagem> mensagens = chatMensagemRepository.findBySessaoIdOrderByCriadoEm(sessaoId);
        assertThat(mensagens).extracting(ChatMensagem::getPapel)
                .containsExactly(PapelMensagem.USUARIO, PapelMensagem.ASSISTENTE);
        assertThat(mensagens.get(1).getFontes()).isNotEmpty()
                .allSatisfy(fonte -> {
                    assertThat(fonte.conteudoId()).isEqualTo(ferias);
                    assertThat(fonte.trecho()).contains("Cada colaborador tem trinta dias de férias por ano.");
                });
        ChatSessao sessao = chatSessaoRepository.findById(sessaoId).orElseThrow();
        assertThat(sessao.getTitulo()).isEqualTo("Quantos dias de férias o colaborador tem?");
    }

    @Test
    void perguntaSemDocumentoRelevanteRecebeNaoEncontreiSemFonte() throws Exception {
        publicaEIndexa("Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        UUID sessaoId = criaSessao(tokenColaborador);

        mockMvc.perform(pergunta(sessaoId, "Qual a senha do wifi da recepção?", tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resposta.conteudo").value(ConsultaAssistenteService.RESPOSTA_NAO_ENCONTREI))
                .andExpect(jsonPath("$.resposta.fontes", hasSize(0)))
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
                .andExpect(jsonPath("$.resposta.conteudo").value(ConsultaAssistenteService.RESPOSTA_NAO_ENCONTREI))
                .andExpect(jsonPath("$.resposta.fontes", hasSize(0)))
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
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM assistente_consulta_limite WHERE tenant_id = ? AND usuario_id = ?",
                Long.class, tenant.getId(), colaborador.getId())).isEqualTo(1L);
    }

    @Test
    void excessoDePerguntasResponde429MesmoEmOutraSessao() throws Exception {
        UUID sessaoQueConsumiuQuota = criaSessao(tokenColaborador);
        UUID sessaoId = criaSessao(tokenColaborador);
        assertThat(sessaoId).isNotEqualTo(sessaoQueConsumiuQuota);
        OffsetDateTime agora = OffsetDateTime.now();
        jdbc.update("INSERT INTO assistente_limite_usuario (tenant_id, usuario_id) VALUES (?, ?) " +
                        "ON CONFLICT (tenant_id, usuario_id) DO NOTHING", tenant.getId(), colaborador.getId());
        for (int i = 0; i < 10; i++) {
            jdbc.update("INSERT INTO assistente_consulta_limite (tenant_id, usuario_id, solicitada_em) " +
                            "VALUES (?, ?, ?)", tenant.getId(), colaborador.getId(), agora);
        }

        var resposta = mockMvc.perform(pergunta(sessaoId, "Mais uma?", tokenColaborador))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andReturn();
        long retryAfter = Long.parseLong(resposta.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        // Pode ser menor que 60 se o tempo passar entre a reserva dos registros e a requisição.
        assertThat(retryAfter).isBetween(1L, 60L);
    }

    @Test
    void perguntaVaziaOuGrandeDemaisResponde400() throws Exception {
        UUID sessaoId = criaSessao(tokenColaborador);

        mockMvc.perform(pergunta(sessaoId, "   ", tokenColaborador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("conteudo"));
        mockMvc.perform(pergunta(sessaoId, "a".repeat(2001), tokenColaborador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("conteudo"));
    }

    @Test
    void aceitaOApelidoPerguntaNoCorpoDaMensagem() throws Exception {
        publicaEIndexa("Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        UUID sessaoId = criaSessao(tokenColaborador);

        // `conteudo` é o nome do contrato; `pergunta` continua aceito para
        // não quebrar quem integrou com o nome antigo.
        mockMvc.perform(comToken(post("/api/assistente/sessoes/{id}/mensagens", sessaoId), tokenColaborador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pergunta", "Quantos dias de férias o colaborador tem?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resposta.conteudo", startsWith(ClienteIaFake.PREFIXO_RESPOSTA)));
    }

    @Test
    void aSessaoPodeNascerComOTituloEscolhidoPorQuemAbre() throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/assistente/sessoes"), tokenColaborador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", "Dúvidas de férias"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Dúvidas de férias"))
                .andExpect(jsonPath("$.criadoEm").isNotEmpty())
                .andExpect(jsonPath("$.atualizadoEm").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID sessaoId = UUID.fromString(objectMapper.readTree(resposta).get("id").asText());

        // O título escolhido não é sobrescrito pela primeira pergunta.
        publicaEIndexa("Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        mockMvc.perform(pergunta(sessaoId, "Quantos dias de férias o colaborador tem?", tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessao.titulo").value("Dúvidas de férias"));
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(post("/api/assistente/sessoes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/assistente/sessoes")).andExpect(status().isUnauthorized());
    }

    @Test
    void listaSoAsSessoesDoProprioUsuarioDaMaisRecenteParaAMaisAntiga() throws Exception {
        publicaEIndexa("Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        UUID primeira = criaSessao(tokenColaborador);
        UUID segunda = criaSessao(tokenColaborador);
        criaSessao(tokenAdmin);
        mockMvc.perform(pergunta(primeira, "Quantos dias de férias o colaborador tem?", tokenColaborador))
                .andExpect(status().isOk());
        mockMvc.perform(pergunta(segunda, "E quando posso tirar as férias?", tokenColaborador))
                .andExpect(status().isOk());

        mockMvc.perform(comToken(get("/api/assistente/sessoes"), tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(segunda.toString()))
                .andExpect(jsonPath("$.content[1].id").value(primeira.toString()))
                .andExpect(jsonPath("$.content[0].titulo").value("E quando posso tirar as férias?"));
    }

    @Test
    void asMensagensVoltamNaOrdemEmQueForamTrocadas() throws Exception {
        UUID ferias = publicaEIndexa("Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        UUID sessaoId = criaSessao(tokenColaborador);
        mockMvc.perform(pergunta(sessaoId, "Quantos dias de férias o colaborador tem?", tokenColaborador))
                .andExpect(status().isOk());

        mockMvc.perform(comToken(get("/api/assistente/sessoes/{id}/mensagens", sessaoId), tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].papel").value("USUARIO"))
                .andExpect(jsonPath("$[0].conteudo").value("Quantos dias de férias o colaborador tem?"))
                .andExpect(jsonPath("$[0].fontes", hasSize(0)))
                .andExpect(jsonPath("$[1].papel").value("ASSISTENTE"))
                .andExpect(jsonPath("$[1].fontes[0]").value("Política de férias"));
    }

    @Test
    void ultimaInteracaoMudaACadaMensagemNova() throws Exception {
        publicaEIndexa("Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        UUID sessaoId = criaSessao(tokenColaborador);
        OffsetDateTime aoCriar = chatSessaoRepository.findById(sessaoId).orElseThrow().getUltimaInteracao();

        mockMvc.perform(pergunta(sessaoId, "Quantos dias de férias o colaborador tem?", tokenColaborador))
                .andExpect(status().isOk());

        assertThat(chatSessaoRepository.findById(sessaoId).orElseThrow().getUltimaInteracao()).isAfter(aoCriar);
    }

    @Test
    void renomeiaAConversaEscolhidaPeloUsuario() throws Exception {
        UUID sessaoId = criaSessao(tokenColaborador);

        mockMvc.perform(comToken(patch("/api/assistente/sessoes/{id}", sessaoId), tokenColaborador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", "Dúvidas de RH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Dúvidas de RH"));

        assertThat(chatSessaoRepository.findById(sessaoId).orElseThrow().getTitulo()).isEqualTo("Dúvidas de RH");
    }

    @Test
    void renomearComTituloVazioOuGrandeDemaisResponde400() throws Exception {
        UUID sessaoId = criaSessao(tokenColaborador);

        mockMvc.perform(renomear(sessaoId, "   ", tokenColaborador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("titulo"));
        mockMvc.perform(renomear(sessaoId, "a".repeat(201), tokenColaborador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("titulo"));
    }

    @Test
    void apagarASessaoApagaAsMensagensDela() throws Exception {
        publicaEIndexa("Política de férias", "Cada colaborador tem trinta dias de férias por ano.");
        UUID sessaoId = criaSessao(tokenColaborador);
        mockMvc.perform(pergunta(sessaoId, "Quantos dias de férias o colaborador tem?", tokenColaborador))
                .andExpect(status().isOk());
        assertThat(chatMensagemRepository.findBySessaoIdOrderByCriadoEm(sessaoId)).isNotEmpty();

        mockMvc.perform(comToken(delete("/api/assistente/sessoes/{id}", sessaoId), tokenColaborador))
                .andExpect(status().isNoContent());

        assertThat(chatSessaoRepository.findById(sessaoId)).isEmpty();
        assertThat(chatMensagemRepository.findBySessaoIdOrderByCriadoEm(sessaoId)).isEmpty();
    }

    @Test
    void conversaDeOutroUsuarioResponde404EmTodasAsRotas() throws Exception {
        UUID doColaborador = criaSessao(tokenColaborador);

        mockMvc.perform(comToken(get("/api/assistente/sessoes/{id}/mensagens", doColaborador), tokenAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(renomear(doColaborador, "Tentativa", tokenAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(delete("/api/assistente/sessoes/{id}", doColaborador), tokenAdmin))
                .andExpect(status().isNotFound());

        assertThat(chatSessaoRepository.findById(doColaborador)).isPresent();
    }

    private MockHttpServletRequestBuilder renomear(UUID sessaoId, String titulo, String token) throws Exception {
        return comToken(patch("/api/assistente/sessoes/{id}", sessaoId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("titulo", titulo)));
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
                .content(json(Map.of("conteudo", texto)));
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
