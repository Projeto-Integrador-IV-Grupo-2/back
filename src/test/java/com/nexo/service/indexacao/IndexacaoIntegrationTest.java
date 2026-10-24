package com.nexo.service.indexacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.entity.ConteudoChunk;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusIndexacao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.event.ArquivoEnviadoEvent;
import com.nexo.repository.ConteudoChunkRepository;
import com.nexo.repository.IndexacaoConteudoRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Ponta a ponta da issue #58 contra PostgreSQL + pgvector reais (Testcontainers)
 * e o {@code ClienteIaFake}.
 *
 * <p><strong>Sem {@code @Transactional} de propósito:</strong> a indexação é um
 * {@code @TransactionalEventListener(AFTER_COMMIT)} assíncrono e só dispara
 * quando a transação da publicação faz commit de verdade. Por isso cada teste
 * cria o próprio tenant e espera a indexação com Awaitility.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IndexacaoIntegrationTest {

    private static final String SENHA = "senha-de-teste-123";
    private static final Duration ESPERA = Duration.ofSeconds(10);

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
    private ConteudoChunkRepository chunkRepository;

    @Autowired
    private IndexacaoConteudoRepository indexacaoRepository;

    @Autowired
    private BuscaDeTrechosService buscaDeTrechosService;

    @Autowired
    private ApplicationEventPublisher publisher;

    private Tenant tenant;
    private String token;

    @BeforeEach
    void prepara() throws Exception {
        tenant = criaTenant();
        token = tokenDe(criaUsuario(tenant, Perfil.ADMIN));
    }

    @Test
    void publicarGeraTrechosIndexadosEBuscaveis() throws Exception {
        UUID id = criaTexto(token, "Política de férias", "Cada colaborador tem trinta dias de férias por ano.");

        publica(token, id);

        await().atMost(ESPERA).until(() -> chunkRepository.countByConteudoId(id) > 0);
        List<ConteudoChunk> chunks = chunkRepository.findByConteudoIdOrderByOrdem(id);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getIndexadoEm()).isNotNull();
            assertThat(chunk.getTenantId()).isEqualTo(tenant.getId());
        });
        assertThat(indexacaoRepository.findById(id)).get()
                .extracting(indexacao -> indexacao.getStatus()).isEqualTo(StatusIndexacao.INDEXADO);

        List<TrechoEncontrado> encontrados = buscaDeTrechosService.buscar(tenant.getId(), "quantos dias de férias?", 5);
        assertThat(encontrados).isNotEmpty();
        assertThat(encontrados.get(0).conteudoId()).isEqualTo(id);
        assertThat(encontrados.get(0).titulo()).isEqualTo("Política de férias");
    }

    @Test
    void editarConteudoPublicadoReindexa() throws Exception {
        UUID id = criaTexto(token, "Reembolso", "Despesas de viagem são reembolsadas em até dez dias.");
        publica(token, id);
        await().atMost(ESPERA).until(() -> chunkRepository.countByConteudoId(id) > 0);

        mockMvc.perform(comToken(put("/api/conteudos/{id}", id), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", "Reembolso", "tipo", "TEXTO",
                                "corpo", "Notas fiscais de combustível entram no reembolso mensal."))))
                .andExpect(status().isOk());

        await().atMost(ESPERA).until(() -> chunkRepository.findByConteudoIdOrderByOrdem(id).stream()
                .anyMatch(chunk -> chunk.getTexto().contains("combustível")));
        assertThat(chunkRepository.findByConteudoIdOrderByOrdem(id))
                .noneMatch(chunk -> chunk.getTexto().contains("viagem"));
    }

    @Test
    void despublicarRemoveOsTrechosETiraDaBusca() throws Exception {
        UUID id = criaTexto(token, "Plano de saúde", "O plano de saúde cobre consultas e exames.");
        publica(token, id);
        await().atMost(ESPERA).until(() -> chunkRepository.countByConteudoId(id) > 0);

        mockMvc.perform(comToken(patch("/api/conteudos/{id}/despublicar", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RASCUNHO"));

        assertThat(buscaDeTrechosService.buscar(tenant.getId(), "plano de saúde cobre consultas", 5)).isEmpty();
        await().atMost(ESPERA).until(() -> chunkRepository.countByConteudoId(id) == 0);
        assertThat(indexacaoRepository.findById(id)).isEmpty();
    }

    @Test
    void buscaNuncaDevolveTrechoDeOutroTenant() throws Exception {
        UUID nosso = criaTexto(token, "Horário", "O expediente começa às nove horas da manhã.");
        publica(token, nosso);
        Tenant outro = criaTenant();
        String tokenOutro = tokenDe(criaUsuario(outro, Perfil.ADMIN));
        UUID deles = criaTexto(tokenOutro, "Horário", "O expediente começa às nove horas da manhã.");
        publica(tokenOutro, deles);
        await().atMost(ESPERA).until(() -> chunkRepository.countByConteudoId(nosso) > 0
                && chunkRepository.countByConteudoId(deles) > 0);

        List<TrechoEncontrado> encontrados = buscaDeTrechosService.buscar(tenant.getId(), "expediente nove horas", 10);

        assertThat(encontrados).isNotEmpty().allSatisfy(trecho -> assertThat(trecho.conteudoId()).isEqualTo(nosso));
    }

    @Test
    void rascunhoNaoEhIndexadoNemAparece() throws Exception {
        UUID id = criaTexto(token, "Rascunho secreto", "Reestruturação prevista para dezembro.");

        publisher.publishEvent(new ArquivoEnviadoEvent(UUID.randomUUID(), id, tenant.getId()));

        await().during(Duration.ofMillis(500)).atMost(ESPERA).until(() -> chunkRepository.countByConteudoId(id) == 0);
        assertThat(buscaDeTrechosService.buscar(tenant.getId(), "reestruturação dezembro", 5)).isEmpty();
    }

    private UUID criaTexto(String tokenAutor, String titulo, String corpo) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/conteudos"), tokenAutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", titulo, "tipo", "TEXTO", "corpo", corpo))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private void publica(String tokenAutor, UUID id) throws Exception {
        mockMvc.perform(comToken(patch("/api/conteudos/{id}/publicar", id), tokenAutor))
                .andExpect(status().isOk());
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
        usuario.setEmail("indexacao-" + UUID.randomUUID() + "@nexo.com");
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
