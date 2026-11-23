package com.nexo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.empresa.CriarEmpresaRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de empresas: só o ADMIN da plataforma (tenant "nexo") cria e lista. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmpresaControllerTest {

    private static final String ADMIN_PLATAFORMA = "admin@nexo.com";
    private static final String SENHA = "Senha1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private CriarEmpresaRequest novaEmpresa(String sufixo) {
        return new CriarEmpresaRequest("Vértice " + sufixo, "vertice-" + sufixo, "12.345.678/0001-90",
                "Admin Vértice", "admin." + sufixo + "@vertice.com", SENHA);
    }

    private String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @WithUserDetails(value = ADMIN_PLATAFORMA, userDetailsServiceBeanName = "customUserDetailsService",
            setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void adminDaPlataformaCriaEmpresaComAdministradorQueConsegueEntrar() throws Exception {
        String sufixo = sufixo();
        CriarEmpresaRequest request = novaEmpresa(sufixo);

        mockMvc.perform(post("/api/admin/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subdominio").value("vertice-" + sufixo))
                .andExpect(jsonPath("$.totalUsuarios").value(1))
                .andExpect(jsonPath("$.plataforma").value(false));

        mockMvc.perform(get("/api/admin/empresas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.subdominio == 'vertice-" + sufixo + "')]").exists())
                .andExpect(jsonPath("$[?(@.subdominio == 'nexo')].plataforma").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "%s"}
                                """.formatted(request.emailAdministrador(), SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.papel").value("ADMIN"))
                .andExpect(jsonPath("$.usuario.empresa.nome").value("Vértice " + sufixo))
                .andExpect(jsonPath("$.usuario.plataforma").value(false));
    }

    @Test
    @WithUserDetails(value = ADMIN_PLATAFORMA, userDetailsServiceBeanName = "customUserDetailsService",
            setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void subdominioRepetidoRetorna400NoCampo() throws Exception {
        String sufixo = sufixo();
        String corpo = objectMapper.writeValueAsString(novaEmpresa(sufixo));

        mockMvc.perform(post("/api/admin/empresas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/empresas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("subdominio"));
    }

    @Test
    @WithUserDetails(value = ADMIN_PLATAFORMA, userDetailsServiceBeanName = "customUserDetailsService",
            setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void subdominioInvalidoESenhaFracaRetornam400() throws Exception {
        CriarEmpresaRequest invalida = new CriarEmpresaRequest("X", "-ruim-", null, "Admin", "a@b.com", "fraca");

        mockMvc.perform(post("/api/admin/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalida)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'subdominio')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'senhaAdministrador')]").exists());
    }

    private String token(String email, String senha) throws Exception {
        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "%s"}
                                """.formatted(email, senha)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(login).get("token").asText();
    }

    @Test
    void adminDeOutraEmpresaRecebe403() throws Exception {
        CriarEmpresaRequest request = novaEmpresa(sufixo());
        mockMvc.perform(post("/api/admin/empresas")
                        .header("Authorization", "Bearer " + token(ADMIN_PLATAFORMA, "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        String tokenDaVertice = token(request.emailAdministrador(), SENHA);
        mockMvc.perform(get("/api/admin/empresas").header("Authorization", "Bearer " + tokenDaVertice))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/empresas")
                        .header("Authorization", "Bearer " + tokenDaVertice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaEmpresa(sufixo()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COLABORADOR")
    void colaboradorRecebe403() throws Exception {
        mockMvc.perform(get("/api/admin/empresas")).andExpect(status().isForbidden());
    }
}
