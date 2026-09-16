package com.nexo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.usuario.AtualizarSituacaoRequest;
import com.nexo.dto.usuario.AtualizarUsuarioRequest;
import com.nexo.dto.usuario.CriarUsuarioRequest;
import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.SituacaoUsuario;
import com.nexo.entity.Tenant;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de integração de ponta a ponta (contexto Spring completo +
 * Postgres efêmero via Testcontainers, igual a {@code HealthControllerTest}).
 *
 * <p>Usa o administrador seedado por {@code V5__seed_initial_admin.sql}
 * ({@code admin@nexo.com}, tenant "nexo") como requisitante — assim o
 * tenant é resolvido de verdade pelo {@code UsuarioService}, igual em
 * produção. {@code @Transactional} garante que cada teste roda numa
 * transação revertida ao final, isolando os dados de um teste dos demais.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UsuarioControllerTest {

    private static final String ADMIN_EMAIL = "admin@nexo.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AreaRepository areaRepository;

    private UUID adminId;

    @BeforeEach
    void carregaAdminSeed() {
        adminId = usuarioRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getId();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void administradorCadastraUsuarioERecebe201SemSenhaNaResposta() throws Exception {
        CriarUsuarioRequest request = novoRequestValido("joana.souza");

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value(request.email()))
                .andExpect(jsonPath("$.situacao").value("ATIVO"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void payloadIncompletoRetorna400ComErroPorCampo() throws Exception {
        String payloadInvalido = """
                {"nomeCompleto": "", "email": "email-invalido", "papel": "COLABORADOR"}
                """;

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void senhaAbaixoDaPoliticaMinimaRetorna400() throws Exception {
        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "Usuario Teste", "senha.fraca." + UUID.randomUUID() + "@empresa.com",
                "12345678", Perfil.COLABORADOR, null, null, LocalDate.now());

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'senha')]").exists());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void emailDuplicadoNoMesmoTenantRetorna409() throws Exception {
        CriarUsuarioRequest request = novoRequestValido("duplicado");

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void idInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/usuarios/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void idComFormatoInvalidoRetorna400EmVezDe500() throws Exception {
        mockMvc.perform(get("/api/usuarios/{id}", "nao-e-um-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void filtroDeListagemComValorInvalidoRetorna400EmVezDe500() throws Exception {
        // "papel" é um enum (Perfil); um valor fora do enum falha o bind do
        // @ModelAttribute (BindException), não o MethodArgumentTypeMismatchException
        // usado por path variables — os dois precisam de handlers separados.
        mockMvc.perform(get("/api/usuarios").param("papel", "NAO_EXISTE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "COLABORADOR")
    void colaboradorRecebe403EmTodosOsEndpoints() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requisicaoNaoAutenticadaNaoAcessaOModulo() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void listaUsuariosComPaginacao() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novoRequestValido("listagem"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/usuarios").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.size").value(5));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void filtraUsuariosPorArea() throws Exception {
        Tenant tenant = tenantRepository.findBySubdominio("nexo").orElseThrow();
        Area area = new Area();
        area.setTenant(tenant);
        area.setNome("Financeiro-" + UUID.randomUUID());
        area = areaRepository.saveAndFlush(area);

        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "Usuario Filtro", "filtro." + UUID.randomUUID() + "@empresa.com",
                "SenhaForte1", Perfil.COLABORADOR, null, area.getId(), LocalDate.now());

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/usuarios").param("areaId", area.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].area.id").value(area.getId().toString()));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void rejeitaAreaDeOutroTenantAoCadastrar() throws Exception {
        Tenant outroTenant = new Tenant();
        outroTenant.setNome("Outra Empresa " + UUID.randomUUID());
        outroTenant.setSubdominio("outra-" + UUID.randomUUID());
        outroTenant = tenantRepository.saveAndFlush(outroTenant);

        Area areaDeOutroTenant = new Area();
        areaDeOutroTenant.setTenant(outroTenant);
        areaDeOutroTenant.setNome("RH");
        areaDeOutroTenant = areaRepository.saveAndFlush(areaDeOutroTenant);

        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "Usuario Cross Tenant", "crosstenant." + UUID.randomUUID() + "@empresa.com",
                "SenhaForte1", Perfil.COLABORADOR, null, areaDeOutroTenant.getId(), LocalDate.now());

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void editaUsuarioExistente() throws Exception {
        String id = cadastraEExtraiId(novoRequestValido("carlos.lima"));

        AtualizarUsuarioRequest atualizacao = new AtualizarUsuarioRequest(
                "Carlos Lima Junior", null, null, Perfil.ADMIN);

        mockMvc.perform(put("/api/usuarios/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(atualizacao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeCompleto").value("Carlos Lima Junior"))
                .andExpect(jsonPath("$.papel").value("ADMIN"));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void ativaEDesativaUsuarioSemExcluir() throws Exception {
        String id = cadastraEExtraiId(novoRequestValido("ativa.desativa"));

        mockMvc.perform(patch("/api/usuarios/{id}/situacao", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtualizarSituacaoRequest(SituacaoUsuario.INATIVO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("INATIVO"));

        // O usuário continua existindo (não foi excluído fisicamente) — GET ainda o retorna.
        mockMvc.perform(get("/api/usuarios/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("INATIVO"));

        mockMvc.perform(patch("/api/usuarios/{id}/situacao", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtualizarSituacaoRequest(SituacaoUsuario.ATIVO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("ATIVO"));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void administradorNaoConsegueDesativarAPropriaConta() throws Exception {
        mockMvc.perform(patch("/api/usuarios/{id}/situacao", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtualizarSituacaoRequest(SituacaoUsuario.INATIVO))))
                .andExpect(status().isConflict());
    }

    private CriarUsuarioRequest novoRequestValido(String prefixoEmail) {
        return new CriarUsuarioRequest(
                "Usuario " + prefixoEmail,
                prefixoEmail + "." + UUID.randomUUID() + "@empresa.com",
                "SenhaForte1",
                Perfil.COLABORADOR,
                null,
                null,
                LocalDate.now().minusMonths(1));
    }

    private String cadastraEExtraiId(CriarUsuarioRequest request) throws Exception {
        String responseBody = mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(responseBody).get("id").asText();
    }
}
