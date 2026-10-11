package com.nexo.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.Usuario;
import com.nexo.exception.CampoInvalidoException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.service.video.VideoVimeo;
import com.nexo.service.video.VimeoClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ponta a ponta da issue #40: categorias, conteúdos, etapas e progresso por
 * etapa, com contexto Spring completo e Postgres efêmero via Testcontainers.
 *
 * <p>O {@link VimeoClient} é substituído por mock: os testes não dependem da
 * rede nem de um vídeo continuar público no Vimeo. O cliente real é coberto
 * por {@code VimeoClientTest}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConteudoEtapaControllerTest {

    private static final String SENHA = "senha-de-teste-123";
    private static final String VIDEO_OK = "https://vimeo.com/22439234";
    private static final String VIDEO_INEXISTENTE = "https://vimeo.com/1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TrilhaRepository trilhaRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private VimeoClient vimeoClient;

    private Tenant tenant;
    private Usuario admin;
    private Usuario colaborador;
    private String tokenAdmin;
    private String tokenColaborador;

    @BeforeEach
    void preparaUsuarios() throws Exception {
        when(vimeoClient.consultar(eq(VIDEO_OK)))
                .thenReturn(new VideoVimeo(185, "https://i.vimeocdn.com/video/1.jpg", "The Mountain"));
        when(vimeoClient.consultar(eq(VIDEO_INEXISTENTE)))
                .thenThrow(new CampoInvalidoException("videoUrl", "Vídeo não encontrado no Vimeo"));

        tenant = criaTenant();
        admin = criaUsuario(tenant, Perfil.ADMIN);
        colaborador = criaUsuario(tenant, Perfil.COLABORADOR);
        tokenAdmin = tokenDe(admin);
        tokenColaborador = tokenDe(colaborador);
    }

    // ---- Categorias ----------------------------------------------------------

    @Test
    void gestaoCriaListaEEditaCategoria() throws Exception {
        String id = idDe(mockMvc.perform(comToken(post("/api/categorias"), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Onboarding"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Onboarding"))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(comToken(put("/api/categorias/{id}", id), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Integração"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Integração"));

        mockMvc.perform(comToken(get("/api/categorias"), tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value("Integração"));
    }

    @Test
    void categoriaComNomeRepetidoRetorna409() throws Exception {
        criaCategoria("Segurança");

        mockMvc.perform(comToken(post("/api/categorias"), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "segurança"))))
                .andExpect(status().isConflict());
    }

    @Test
    void colaboradorNaoCriaCategoria() throws Exception {
        mockMvc.perform(comToken(post("/api/categorias"), tokenColaborador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Qualquer"))))
                .andExpect(status().isForbidden());
    }

    // ---- Conteúdos -----------------------------------------------------------

    @Test
    void criaVideoComDuracaoEMiniaturaDoVimeo() throws Exception {
        UUID categoriaId = criaCategoria("Vídeos");

        mockMvc.perform(comToken(post("/api/conteudos"), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "titulo", "Boas-vindas",
                                "tipo", "VIDEO",
                                "videoUrl", VIDEO_OK,
                                "categoriaId", categoriaId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.duracaoSegundos").value(185))
                .andExpect(jsonPath("$.miniaturaUrl").value("https://i.vimeocdn.com/video/1.jpg"))
                .andExpect(jsonPath("$.categoria.id").value(categoriaId.toString()));
    }

    @Test
    void linkInvalidoDoVimeoRetorna400NoCampo() throws Exception {
        mockMvc.perform(comToken(post("/api/conteudos"), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", "Quebrado", "tipo", "VIDEO", "videoUrl", VIDEO_INEXISTENTE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("videoUrl"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Vídeo não encontrado no Vimeo"));
    }

    @Test
    void textoSemCorpoRetorna400NoCampo() throws Exception {
        mockMvc.perform(comToken(post("/api/conteudos"), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", "Sem corpo", "tipo", "TEXTO"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("corpo"));
    }

    @Test
    void colaboradorRecebe403AoCriarEditarOuPublicarConteudo() throws Exception {
        UUID conteudoId = criaConteudoTexto("Rascunho", false);

        mockMvc.perform(comToken(post("/api/conteudos"), tokenColaborador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", "X", "tipo", "TEXTO", "corpo", "x"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(comToken(put("/api/conteudos/{id}", conteudoId), tokenColaborador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", "X", "tipo", "TEXTO", "corpo", "x"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(comToken(patch("/api/conteudos/{id}/publicar", conteudoId), tokenColaborador))
                .andExpect(status().isForbidden());
    }

    @Test
    void colaboradorNaoVeConteudoEmRascunho() throws Exception {
        UUID rascunho = criaConteudoTexto("Rascunho secreto", false);
        UUID publicado = criaConteudoTexto("Publicado", true);

        mockMvc.perform(comToken(get("/api/conteudos"), tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(publicado.toString()));
        mockMvc.perform(comToken(get("/api/conteudos/{id}", rascunho), tokenColaborador))
                .andExpect(status().isNotFound());

        // A gestão enxerga os dois.
        mockMvc.perform(comToken(get("/api/conteudos"), tokenAdmin))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void listagemFiltraPorTipoEArea() throws Exception {
        Area area = criaArea("Comercial");
        criaConteudoTexto("Texto solto", true);
        String id = idDe(mockMvc.perform(comToken(post("/api/conteudos"), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", "Vídeo da área", "tipo", "VIDEO",
                                "videoUrl", VIDEO_OK, "areaId", area.getId()))))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(comToken(get("/api/conteudos").param("tipo", "VIDEO").param("areaId",
                        area.getId().toString()), tokenAdmin))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));

        // AreaResponse.totalConteudos passa a contar de conteudo.area_id.
        mockMvc.perform(comToken(get("/api/areas/{id}", area.getId()), tokenAdmin))
                .andExpect(jsonPath("$.totalConteudos").value(1));
    }

    @Test
    void conteudoDeOutroTenantRetorna404() throws Exception {
        UUID conteudoId = criaConteudoTexto("Meu", true);
        Usuario adminDeFora = criaUsuario(criaTenant(), Perfil.ADMIN);
        String tokenDeFora = tokenDe(adminDeFora);

        mockMvc.perform(comToken(get("/api/conteudos/{id}", conteudoId), tokenDeFora))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(patch("/api/conteudos/{id}/publicar", conteudoId), tokenDeFora))
                .andExpect(status().isNotFound());
    }

    // ---- Etapas e progresso --------------------------------------------------

    @Test
    void etapasNascemEmSequenciaEReordenam() throws Exception {
        Trilha trilha = criaTrilha();
        UUID primeira = criaEtapa(trilha, criaConteudoTexto("A", true));
        UUID segunda = criaEtapa(trilha, criaConteudoTexto("B", true));

        mockMvc.perform(comToken(get("/api/trilhas/{id}/etapas", trilha.getId()), tokenColaborador))
                .andExpect(jsonPath("$[0].id").value(primeira.toString()))
                .andExpect(jsonPath("$[0].ordem").value(1))
                .andExpect(jsonPath("$[1].ordem").value(2));

        mockMvc.perform(comToken(patch("/api/trilhas/{id}/etapas/ordem", trilha.getId()), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapaIds", List.of(segunda, primeira)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(segunda.toString()))
                .andExpect(jsonPath("$[0].ordem").value(1))
                .andExpect(jsonPath("$[1].ordem").value(2));
    }

    @Test
    void reordenarComEtapaFaltandoOuRepetidaRetorna400() throws Exception {
        Trilha trilha = criaTrilha();
        UUID primeira = criaEtapa(trilha, criaConteudoTexto("A", true));
        criaEtapa(trilha, criaConteudoTexto("B", true));

        mockMvc.perform(comToken(patch("/api/trilhas/{id}/etapas/ordem", trilha.getId()), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapaIds", List.of(primeira, primeira)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void etapaComConteudoEmRascunhoRetorna409() throws Exception {
        Trilha trilha = criaTrilha();
        UUID rascunho = criaConteudoTexto("Rascunho", false);

        mockMvc.perform(comToken(post("/api/trilhas/{id}/etapas", trilha.getId()), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("conteudoId", rascunho))))
                .andExpect(status().isConflict());
    }

    @Test
    void colaboradorRecebe403AoMontarEtapas() throws Exception {
        Trilha trilha = criaTrilha();
        UUID conteudo = criaConteudoTexto("A", true);
        UUID etapa = criaEtapa(trilha, conteudo);

        mockMvc.perform(comToken(post("/api/trilhas/{id}/etapas", trilha.getId()), tokenColaborador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("conteudoId", conteudo))))
                .andExpect(status().isForbidden());
        mockMvc.perform(comToken(put("/api/trilhas/{id}/etapas/{etapaId}", trilha.getId(), etapa), tokenColaborador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("conteudoId", conteudo))))
                .andExpect(status().isForbidden());
        mockMvc.perform(comToken(delete("/api/trilhas/{id}/etapas/{etapaId}", trilha.getId(), etapa),
                        tokenColaborador))
                .andExpect(status().isForbidden());
        mockMvc.perform(comToken(patch("/api/trilhas/{id}/etapas/ordem", trilha.getId()), tokenColaborador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapaIds", List.of(etapa)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void concluirTodasAsEtapasConcluiATrilha() throws Exception {
        Trilha trilha = criaTrilha();
        UUID primeira = criaEtapa(trilha, criaConteudoTexto("A", true));
        UUID segunda = criaEtapa(trilha, criaConteudoTexto("B", true));

        mockMvc.perform(comToken(post("/api/trilhas/{id}/etapas/{etapaId}/concluir", trilha.getId(), primeira),
                        tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaId").value(primeira.toString()))
                .andExpect(jsonPath("$.progresso.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.progresso.percentual").value(50));

        // Repetir a mesma etapa não conta duas vezes.
        mockMvc.perform(comToken(post("/api/trilhas/{id}/etapas/{etapaId}/concluir", trilha.getId(), primeira),
                        tokenColaborador))
                .andExpect(jsonPath("$.progresso.percentual").value(50));

        mockMvc.perform(comToken(post("/api/trilhas/{id}/etapas/{etapaId}/concluir", trilha.getId(), segunda),
                        tokenColaborador))
                .andExpect(jsonPath("$.progresso.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.progresso.percentual").value(100))
                .andExpect(jsonPath("$.progresso.concluidoEm").isNotEmpty());

        mockMvc.perform(comToken(get("/api/trilhas/{id}/etapas", trilha.getId()), tokenColaborador))
                .andExpect(jsonPath("$[0].concluida").value(true))
                .andExpect(jsonPath("$[1].concluida").value(true));
    }

    @Test
    void adicionarEtapaATrilhaEmAndamentoReduzOPercentual() throws Exception {
        Trilha trilha = criaTrilha();
        UUID primeira = criaEtapa(trilha, criaConteudoTexto("A", true));
        criaEtapa(trilha, criaConteudoTexto("B", true));
        concluir(trilha, primeira, tokenColaborador);

        criaEtapa(trilha, criaConteudoTexto("C", true));

        mockMvc.perform(comToken(get("/api/trilhas/{id}/progresso", trilha.getId()), tokenColaborador))
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.percentual").value(33));
    }

    @Test
    void trilhaConcluidaPermaneceConcluidaAoGanharEtapa() throws Exception {
        Trilha trilha = criaTrilha();
        UUID unica = criaEtapa(trilha, criaConteudoTexto("A", true));
        concluir(trilha, unica, tokenColaborador);

        criaEtapa(trilha, criaConteudoTexto("B", true));

        mockMvc.perform(comToken(get("/api/trilhas/{id}/progresso", trilha.getId()), tokenColaborador))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.percentual").value(100));
    }

    @Test
    void removerEtapaRemoveOProgressoDelaERecalcula() throws Exception {
        Trilha trilha = criaTrilha();
        UUID primeira = criaEtapa(trilha, criaConteudoTexto("A", true));
        UUID segunda = criaEtapa(trilha, criaConteudoTexto("B", true));
        UUID terceira = criaEtapa(trilha, criaConteudoTexto("C", true));
        concluir(trilha, primeira, tokenColaborador);
        concluir(trilha, segunda, tokenColaborador);

        mockMvc.perform(comToken(delete("/api/trilhas/{id}/etapas/{etapaId}", trilha.getId(), primeira), tokenAdmin))
                .andExpect(status().isNoContent());

        // Sobram B (concluída) e C: 1 de 2; e a sequência fecha o buraco.
        mockMvc.perform(comToken(get("/api/trilhas/{id}/progresso", trilha.getId()), tokenColaborador))
                .andExpect(jsonPath("$.percentual").value(50));
        mockMvc.perform(comToken(get("/api/trilhas/{id}/etapas", trilha.getId()), tokenColaborador))
                .andExpect(jsonPath("$[0].id").value(segunda.toString()))
                .andExpect(jsonPath("$[0].ordem").value(1))
                .andExpect(jsonPath("$[1].id").value(terceira.toString()))
                .andExpect(jsonPath("$[1].ordem").value(2));
    }

    @Test
    void usuarioSoAlteraOProprioProgresso() throws Exception {
        Trilha trilha = criaTrilha();
        UUID etapa = criaEtapa(trilha, criaConteudoTexto("A", true));
        criaEtapa(trilha, criaConteudoTexto("B", true));
        Usuario outro = criaUsuario(tenant, Perfil.COLABORADOR);

        concluir(trilha, etapa, tokenColaborador);

        // O progresso do colega não é afetado: ele nem iniciou a trilha.
        mockMvc.perform(comToken(get("/api/trilhas/{id}/progresso", trilha.getId()), tokenDe(outro)))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(get("/api/trilhas/{id}/etapas", trilha.getId()), tokenDe(outro)))
                .andExpect(jsonPath("$[0].concluida").value(false));
    }

    @Test
    void etapaDeOutraTrilhaRetorna404() throws Exception {
        Trilha trilha = criaTrilha();
        Trilha outra = criaTrilha();
        UUID etapaDaOutra = criaEtapa(outra, criaConteudoTexto("A", true));

        mockMvc.perform(comToken(post("/api/trilhas/{id}/etapas/{etapaId}/concluir", trilha.getId(), etapaDaOutra),
                        tokenColaborador))
                .andExpect(status().isNotFound());
    }

    // ---- Apoio -----------------------------------------------------------------

    private void concluir(Trilha trilha, UUID etapaId, String token) throws Exception {
        mockMvc.perform(comToken(post("/api/trilhas/{id}/etapas/{etapaId}/concluir", trilha.getId(), etapaId), token))
                .andExpect(status().isOk());
    }

    private UUID criaEtapa(Trilha trilha, UUID conteudoId) throws Exception {
        return UUID.fromString(idDe(mockMvc.perform(comToken(post("/api/trilhas/{id}/etapas", trilha.getId()), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("conteudoId", conteudoId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()));
    }

    private UUID criaConteudoTexto(String titulo, boolean publicar) throws Exception {
        UUID id = UUID.fromString(idDe(mockMvc.perform(comToken(post("/api/conteudos"), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", titulo, "tipo", "TEXTO", "corpo", "Leia com atenção."))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()));
        if (publicar) {
            mockMvc.perform(comToken(patch("/api/conteudos/{id}/publicar", id), tokenAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PUBLICADO"));
        }
        return id;
    }

    private UUID criaCategoria(String nome) throws Exception {
        return UUID.fromString(idDe(mockMvc.perform(comToken(post("/api/categorias"), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", nome))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()));
    }

    private Area criaArea(String nome) {
        Area area = new Area();
        area.setTenant(tenant);
        area.setNome(nome);
        return areaRepository.saveAndFlush(area);
    }

    private Trilha criaTrilha() {
        Trilha trilha = new Trilha();
        trilha.setTenant(tenant);
        trilha.setNome("Trilha " + UUID.randomUUID());
        trilha.setCriadoPor(admin);
        return trilhaRepository.saveAndFlush(trilha);
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
        usuario.setEmail("conteudo-" + UUID.randomUUID() + "@nexo.com");
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
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private static MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder requisicao, String token) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String json(Object corpo) throws Exception {
        return objectMapper.writeValueAsString(corpo);
    }

    private String idDe(String corpo) throws Exception {
        JsonNode no = objectMapper.readTree(corpo);
        return no.get("id").asText();
    }
}
