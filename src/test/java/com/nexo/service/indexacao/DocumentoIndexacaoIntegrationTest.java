package com.nexo.service.indexacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.repository.ConteudoChunkRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.service.documento.ArquivosDeTeste;
import java.time.Duration;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Documento com arquivos até a busca do assistente (issues #55 e #58), com
 * PostgreSQL + pgvector reais e o {@code ClienteIaFake}: o texto dos arquivos
 * enviados vira trechos buscáveis.
 *
 * <p>Sem {@code @Transactional} de propósito, como o
 * {@link IndexacaoIntegrationTest}: a indexação só dispara depois do commit.
 * Cada teste cria o próprio tenant.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentoIndexacaoIntegrationTest {

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
    private BuscaDeTrechosService buscaDeTrechosService;

    private Tenant tenant;
    private String token;

    @BeforeEach
    void prepara() throws Exception {
        tenant = criaTenant();
        token = tokenDe(criaUsuario(tenant, Perfil.ADMIN));
    }

    @Test
    void publicarDocumentoIndexaOTextoDosArquivos() throws Exception {
        UUID id = criaDocumento("Manual de benefícios");
        envia(id, "beneficios.pdf", ArquivosDeTeste.pdf("O vale alimentacao e depositado todo dia cinco"));

        publica(id);

        await().atMost(ESPERA).until(() -> textoIndexado(id).contains("vale alimentacao"));
        List<TrechoEncontrado> encontrados = buscaDeTrechosService.buscar(
                tenant.getId(), "quando o vale alimentacao e depositado", 5);
        assertThat(encontrados).isNotEmpty();
        assertThat(encontrados.get(0).conteudoId()).isEqualTo(id);
    }

    @Test
    void arquivoNovoEmDocumentoPublicadoReindexa() throws Exception {
        UUID id = criaDocumento("Políticas internas");
        envia(id, "ferias.txt", ArquivosDeTeste.texto("As férias podem ser divididas em até três períodos."));
        publica(id);
        await().atMost(ESPERA).until(() -> textoIndexado(id).contains("férias"));

        envia(id, "home-office.md", ArquivosDeTeste.texto("# Home office\n\nO trabalho remoto vale às sextas-feiras."));

        await().atMost(ESPERA).until(() -> textoIndexado(id).contains("trabalho remoto"));
        assertThat(textoIndexado(id)).contains("férias");
    }

    private String textoIndexado(UUID id) {
        return String.join("\n", chunkRepository.findByConteudoIdOrderByOrdem(id).stream()
                .map(chunk -> chunk.getTexto())
                .toList());
    }

    private UUID criaDocumento(String titulo) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/conteudos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("titulo", titulo, "tipo", "DOCUMENTO"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private void envia(UUID conteudoId, String nome, byte[] bytes) throws Exception {
        mockMvc.perform(comToken(multipart("/api/conteudos/{id}/arquivos", conteudoId)
                        .file(new MockMultipartFile("arquivo", nome, "application/octet-stream", bytes))))
                .andExpect(status().isCreated());
    }

    private void publica(UUID id) throws Exception {
        mockMvc.perform(comToken(patch("/api/conteudos/{id}/publicar", id))).andExpect(status().isOk());
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
        usuario.setEmail("documento-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPapel(papel);
        usuario.setAtivo(true);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private String tokenDe(Usuario usuario) throws Exception {
        String corpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(usuario.getEmail(), SENHA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
