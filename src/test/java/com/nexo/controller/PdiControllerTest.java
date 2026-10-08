package com.nexo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de integração do PDI (issue #46): contexto Spring completo +
 * Postgres efêmero (Testcontainers). Autentica com login de verdade, como
 * {@code TrilhaControllerTest}, porque os controllers usam
 * {@code @AuthenticationPrincipal UsuarioAutenticado}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PdiControllerTest {

    private static final String SENHA = "senha-de-teste-123";

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
    private TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void gestorCriaPdiQueNasceAtivoComEleComoGestor() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        Usuario colaborador = criaUsuario(tenantSeed(), Perfil.COLABORADOR);

        enviar(post("/api/pdis"), tokenDe(gestor), corpoPdi(colaborador, "2026"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("/api/pdis/")))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.gestor.id").value(gestor.getId().toString()))
                .andExpect(jsonPath("$.colaborador.id").value(colaborador.getId().toString()))
                .andExpect(jsonPath("$.percentual").value(0))
                .andExpect(jsonPath("$.itens").isEmpty());
    }

    @Test
    void doisPdisDoMesmoColaboradorNoMesmoCicloRetorna409() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        Usuario colaborador = criaUsuario(tenantSeed(), Perfil.COLABORADOR);
        String token = tokenDe(gestor);
        enviar(post("/api/pdis"), token, corpoPdi(colaborador, "2026")).andExpect(status().isCreated());

        enviar(post("/api/pdis"), token, corpoPdi(colaborador, "2026"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ciclo 2026")));
    }

    @Test
    void colaboradorNaoCriaPdi() throws Exception {
        Usuario colaborador = criaUsuario(tenantSeed(), Perfil.COLABORADOR);

        enviar(post("/api/pdis"), tokenDe(colaborador), corpoPdi(colaborador, "2026"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nivelForaDe1a5Retorna400ComErroNoCampo() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        String token = tokenDe(gestor);
        String pdiId = criaPdi(token, criaUsuario(tenantSeed(), Perfil.COLABORADOR), "2026");
        String competenciaId = criaCompetencia(token);

        enviar(post("/api/pdis/{id}/itens", pdiId), token,
                Map.of("competenciaId", competenciaId, "nivelAtual", 0, "nivelAlvo", 6))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("nivelAtual")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("nivelAlvo")));
    }

    @Test
    void competenciaRepetidaNoPdiRetorna409() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        String token = tokenDe(gestor);
        String pdiId = criaPdi(token, criaUsuario(tenantSeed(), Perfil.COLABORADOR), "2026");
        String competenciaId = criaCompetencia(token);
        Map<String, Object> item = Map.of("competenciaId", competenciaId, "nivelAtual", 2, "nivelAlvo", 4);

        enviar(post("/api/pdis/{id}/itens", pdiId), token, item).andExpect(status().isCreated());
        enviar(post("/api/pdis/{id}/itens", pdiId), token, item)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("já está neste PDI")));
    }

    @Test
    void acaoDeTrilhaSemTrilhaRetorna400NoCampoTrilhaId() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        String token = tokenDe(gestor);
        String pdiId = criaPdi(token, criaUsuario(tenantSeed(), Perfil.COLABORADOR), "2026");
        String itemId = criaItem(token, pdiId, criaCompetencia(token));

        enviar(post("/api/pdis/{id}/itens/{itemId}/acoes", pdiId, itemId), token,
                Map.of("tipo", "TRILHA", "prazo", "2026-06-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("trilhaId"));
    }

    @Test
    void colaboradorVeSoOProprioPdi() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        Usuario colaborador = criaUsuario(tenantSeed(), Perfil.COLABORADOR);
        Usuario outro = criaUsuario(tenantSeed(), Perfil.COLABORADOR);
        String tokenGestor = tokenDe(gestor);
        String meuPdi = criaPdi(tokenGestor, colaborador, "2026");
        String pdiDoOutro = criaPdi(tokenGestor, outro, "2026");
        String token = tokenDe(colaborador);

        mockMvc.perform(get("/api/pdis").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(meuPdi));
        mockMvc.perform(get("/api/pdis/meu").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(meuPdi));
        mockMvc.perform(get("/api/pdis/{id}", pdiDoOutro).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void meuSemPdiAtivoRetorna404() throws Exception {
        Usuario colaborador = criaUsuario(tenantSeed(), Perfil.COLABORADOR);

        mockMvc.perform(get("/api/pdis/meu").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(colaborador)))
                .andExpect(status().isNotFound());
    }

    @Test
    void gestorVeSoOsPdisEmQueEhGestorEAdminVeTodos() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        Usuario outroGestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        String doGestor = criaPdi(tokenDe(gestor), criaUsuario(tenantSeed(), Perfil.COLABORADOR), "2026");
        String doOutro = criaPdi(tokenDe(outroGestor), criaUsuario(tenantSeed(), Perfil.COLABORADOR), "2026");
        String tokenGestor = tokenDe(gestor);

        mockMvc.perform(get("/api/pdis").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(doGestor));
        mockMvc.perform(get("/api/pdis/{id}", doOutro).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGestor))
                .andExpect(status().isNotFound());
        enviar(patch("/api/pdis/{id}/status", doOutro), tokenGestor, Map.of("status", "CANCELADO"))
                .andExpect(status().isNotFound());

        String tokenAdmin = tokenDe(criaUsuario(tenantSeed(), Perfil.ADMIN));
        mockMvc.perform(get("/api/pdis").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(doGestor)))
                .andExpect(jsonPath("$.content[*].id", hasItem(doOutro)));
    }

    @Test
    void pdiDeOutroTenantRetorna404() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        String pdiId = criaPdi(tokenDe(gestor), criaUsuario(tenantSeed(), Perfil.COLABORADOR), "2026");
        Usuario adminDeOutro = criaUsuario(criaTenant(), Perfil.ADMIN);

        mockMvc.perform(get("/api/pdis/{id}", pdiId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(adminDeOutro)))
                .andExpect(status().isNotFound());
    }

    @Test
    void acaoDeTrilhaAtribuiSemDuplicarEConcluirATrilhaConcluiAAcao() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        Usuario colaborador = criaUsuario(tenantSeed(), Perfil.COLABORADOR);
        Trilha trilha = criaTrilha(gestor);
        String tokenGestor = tokenDe(gestor);
        String pdiId = criaPdi(tokenGestor, colaborador, "2026");
        String itemA = criaItem(tokenGestor, pdiId, criaCompetencia(tokenGestor));
        String itemB = criaItem(tokenGestor, pdiId, criaCompetencia(tokenGestor));
        Map<String, Object> acaoTrilha = Map.of("tipo", "TRILHA", "trilhaId", trilha.getId().toString(),
                "prazo", "2026-06-30");

        enviar(post("/api/pdis/{id}/itens/{itemId}/acoes", pdiId, itemA), tokenGestor, acaoTrilha)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.progressoTrilha.status").value("NAO_INICIADA"))
                .andExpect(jsonPath("$.progressoTrilha.prazo").value("2026-06-30"));
        // A mesma trilha em outra competência reaproveita a atribuição.
        enviar(post("/api/pdis/{id}/itens/{itemId}/acoes", pdiId, itemB), tokenGestor, acaoTrilha)
                .andExpect(status().isCreated());
        enviar(post("/api/pdis/{id}/itens/{itemId}/acoes", pdiId, itemB), tokenGestor,
                Map.of("tipo", "MENTORIA", "prazo", "2026-08-31", "descricao", "Mentoria com a gestora"))
                .andExpect(status().isCreated());

        List<TrilhaAtribuicao> atribuicoes = trilhaAtribuicaoRepository
                .findByTenantIdAndUsuarioId(colaborador.getTenant().getId(), colaborador.getId());
        assertThat(atribuicoes).singleElement().satisfies(atribuicao -> {
            assertThat(atribuicao.getStatus()).isEqualTo(StatusAtribuicao.NAO_INICIADA);
            assertThat(atribuicao.getAtribuidoPor().getId()).isEqualTo(gestor.getId());
        });

        // Colaborador inicia (NAO_INICIADA vira EM_ANDAMENTO) e conclui a trilha.
        String tokenColaborador = tokenDe(colaborador);
        mockMvc.perform(post("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
        enviar(patch("/api/trilhas/{id}/progresso", trilha.getId()), tokenColaborador,
                Map.of("status", "CONCLUIDA"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/pdis/meu").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAcoes").value(3))
                .andExpect(jsonPath("$.acoesConcluidas").value(2))
                .andExpect(jsonPath("$.percentual").value(67))
                .andExpect(jsonPath("$.itens[*].acoes[?(@.tipo == 'TRILHA')].status",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("CONCLUIDA"))))
                .andExpect(jsonPath("$.itens[*].acoes[?(@.tipo == 'TRILHA')].progressoTrilha.percentual",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(100))));
    }

    @Test
    void statusDeAcaoDeTrilhaNaoMudaManualmenteMasMentoriaSim() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        String token = tokenDe(gestor);
        String pdiId = criaPdi(token, criaUsuario(tenantSeed(), Perfil.COLABORADOR), "2026");
        String itemId = criaItem(token, pdiId, criaCompetencia(token));
        String acaoTrilha = idDe(enviar(post("/api/pdis/{id}/itens/{itemId}/acoes", pdiId, itemId), token,
                Map.of("tipo", "TRILHA", "trilhaId", criaTrilha(gestor).getId().toString(), "prazo", "2026-06-30")));
        String mentoria = idDe(enviar(post("/api/pdis/{id}/itens/{itemId}/acoes", pdiId, itemId), token,
                Map.of("tipo", "MENTORIA", "prazo", "2026-06-30", "descricao", "Mentoria")));

        enviar(patch("/api/pdis/{id}/itens/{itemId}/acoes/{acaoId}/status", pdiId, itemId, acaoTrilha), token,
                Map.of("status", "CONCLUIDA"))
                .andExpect(status().isConflict());
        enviar(patch("/api/pdis/{id}/itens/{itemId}/acoes/{acaoId}/status", pdiId, itemId, mentoria), token,
                Map.of("status", "CONCLUIDA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));

        mockMvc.perform(get("/api/pdis/{id}", pdiId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.percentual").value(50));
    }

    @Test
    void gestorListaColaboradoresAtivosDoTenantParaCriarPdi() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        Usuario colaborador = criaUsuario(tenantSeed(), Perfil.COLABORADOR);
        Usuario inativo = criaUsuario(tenantSeed(), Perfil.COLABORADOR);
        inativo.setAtivo(false);
        usuarioRepository.saveAndFlush(inativo);
        Usuario deOutroTenant = criaUsuario(criaTenant(), Perfil.COLABORADOR);

        mockMvc.perform(get("/api/pdis/colaboradores").param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(colaborador.getId().toString())))
                .andExpect(jsonPath("$.content[*].id",
                        org.hamcrest.Matchers.not(hasItem(inativo.getId().toString()))))
                .andExpect(jsonPath("$.content[*].id",
                        org.hamcrest.Matchers.not(hasItem(deOutroTenant.getId().toString()))))
                .andExpect(jsonPath("$.content[0].senhaHash").doesNotExist());

        mockMvc.perform(get("/api/pdis/colaboradores")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(colaborador)))
                .andExpect(status().isForbidden());
    }

    @Test
    void competenciasSaoDeAdminEGestorComNomeUnicoPorTenant() throws Exception {
        String tokenGestor = tokenDe(criaUsuario(tenantSeed(), Perfil.GESTOR));
        String nome = "Comunicação " + UUID.randomUUID();
        Map<String, Object> competencia = Map.of("nome", nome, "tipo", "COMPORTAMENTAL");

        enviar(post("/api/competencias"), tokenGestor, competencia).andExpect(status().isCreated());
        enviar(post("/api/competencias"), tokenGestor, competencia).andExpect(status().isConflict());
        // Outro tenant pode usar o mesmo nome.
        enviar(post("/api/competencias"), tokenDe(criaUsuario(criaTenant(), Perfil.ADMIN)), competencia)
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/competencias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(criaUsuario(tenantSeed(), Perfil.COLABORADOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void competenciaInativadaSaiDaListagemPadrao() throws Exception {
        String token = tokenDe(criaUsuario(tenantSeed(), Perfil.ADMIN));
        String id = criaCompetencia(token);

        mockMvc.perform(patch("/api/competencias/{id}/inativar", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(get("/api/competencias").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.not(hasItem(id))));
        mockMvc.perform(get("/api/competencias").param("size", "100").param("incluirInativas", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.content[*].id", hasItem(id)));
    }

    private String criaPdi(String token, Usuario colaborador, String ciclo) throws Exception {
        return idDe(enviar(post("/api/pdis"), token, corpoPdi(colaborador, ciclo)));
    }

    private String criaCompetencia(String token) throws Exception {
        return idDe(enviar(post("/api/competencias"), token,
                Map.of("nome", "Competência " + UUID.randomUUID(), "tipo", "TECNICA")));
    }

    private String criaItem(String token, String pdiId, String competenciaId) throws Exception {
        return idDe(enviar(post("/api/pdis/{id}/itens", pdiId), token,
                Map.of("competenciaId", competenciaId, "nivelAtual", 2, "nivelAlvo", 4)));
    }

    private static Map<String, Object> corpoPdi(Usuario colaborador, String ciclo) {
        return Map.of("colaboradorId", colaborador.getId().toString(), "ciclo", ciclo,
                "inicio", "2026-01-01", "fim", "2026-12-31");
    }

    private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requisicao,
                                 String token, Object corpo) throws Exception {
        return mockMvc.perform(requisicao
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private String idDe(ResultActions resultado) throws Exception {
        String corpo = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(corpo);
        return json.get("id").asText();
    }

    private Tenant tenantSeed() {
        return tenantRepository.findBySubdominio("nexo").orElseThrow();
    }

    private Tenant criaTenant() {
        Tenant tenant = new Tenant();
        tenant.setNome("Outra Empresa " + UUID.randomUUID());
        tenant.setSubdominio("outra-" + UUID.randomUUID());
        return tenantRepository.saveAndFlush(tenant);
    }

    private Usuario criaUsuario(Tenant tenant, Perfil papel) {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Usuário de Teste " + papel);
        usuario.setEmail("pdi-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPapel(papel);
        usuario.setAtivo(true);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private Trilha criaTrilha(Usuario criadoPor) {
        Trilha trilha = new Trilha();
        trilha.setTenant(criadoPor.getTenant());
        trilha.setNome("Trilha de teste " + UUID.randomUUID());
        trilha.setCriadoPor(criadoPor);
        return trilhaRepository.saveAndFlush(trilha);
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
}
