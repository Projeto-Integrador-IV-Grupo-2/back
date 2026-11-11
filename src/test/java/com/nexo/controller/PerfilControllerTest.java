package com.nexo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Perfil do próprio usuário ({@code /api/auth/me}), no contrato que a tela de
 * perfil do front espera: o nome novo aparece no {@code GET /me} com o mesmo
 * token, e senha atual errada é 400 com {@code fieldErrors} — nunca 401, que o
 * front trataria como sessão vencida.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PerfilControllerTest {

    private static final String SENHA = "SenhaAtual1";

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant tenant;
    private Area area;
    private String email;
    private String token;

    @BeforeEach
    void prepara() throws Exception {
        tenant = tenantRepository.findBySubdominio("nexo").orElseThrow();
        area = new Area();
        area.setTenant(tenant);
        area.setNome("Área " + UUID.randomUUID());
        area = areaRepository.saveAndFlush(area);

        email = "perfil-" + UUID.randomUUID() + "@nexo.com";
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Ana Souza");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPapel(Perfil.COLABORADOR);
        usuario.setArea(area);
        usuario.setAtivo(true);
        usuarioRepository.saveAndFlush(usuario);

        token = tokenDe(email, SENHA);
    }

    @Test
    void loginEMeTrazemAreaEEmpresa() throws Exception {
        mockMvc.perform(login(email, SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.area.id").value(area.getId().toString()))
                .andExpect(jsonPath("$.usuario.empresa.nome").value(tenant.getNome()));

        mockMvc.perform(comToken(get("/api/auth/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area.nome").value(area.getNome()))
                .andExpect(jsonPath("$.empresa.id").value(tenant.getId().toString()));
    }

    @Test
    void atualizarNomeDevolveOPerfilEValeNoMeComOMesmoToken() throws Exception {
        mockMvc.perform(comToken(put("/api/auth/me")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nomeCompleto", "  Ana Maria Souza "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeCompleto").value("Ana Maria Souza"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.papel").value("COLABORADOR"));

        mockMvc.perform(comToken(get("/api/auth/me")))
                .andExpect(jsonPath("$.nomeCompleto").value("Ana Maria Souza"));
    }

    @Test
    void nomeEmBrancoDevolve400NoCampo() throws Exception {
        mockMvc.perform(comToken(put("/api/auth/me")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nomeCompleto", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nomeCompleto"));
    }

    @Test
    void trocarSenhaDevolve204EANovaSenhaPassaAValerNoLogin() throws Exception {
        mockMvc.perform(comToken(patch("/api/auth/me/senha")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("senhaAtual", SENHA, "novaSenha", "NovaSenha2"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(login(email, SENHA)).andExpect(status().isUnauthorized());
        mockMvc.perform(login(email, "NovaSenha2")).andExpect(status().isOk());
    }

    @Test
    void senhaAtualErradaDevolve400NoCampoENaoDesloga() throws Exception {
        mockMvc.perform(comToken(patch("/api/auth/me/senha")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("senhaAtual", "Errada123", "novaSenha", "NovaSenha2"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("senhaAtual"));

        mockMvc.perform(comToken(get("/api/auth/me"))).andExpect(status().isOk());
        mockMvc.perform(login(email, SENHA)).andExpect(status().isOk());
    }

    @Test
    void novaSenhaFracaDevolve400NoCampo() throws Exception {
        mockMvc.perform(comToken(patch("/api/auth/me/senha")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("senhaAtual", SENHA, "novaSenha", "fraca"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("novaSenha"));
    }

    @Test
    void novaSenhaIgualAAtualDevolve400NoCampo() throws Exception {
        mockMvc.perform(comToken(patch("/api/auth/me/senha")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("senhaAtual", SENHA, "novaSenha", SENHA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("novaSenha"));
    }

    @Test
    void semTokenAsRotasDoPerfilDevolvem401() throws Exception {
        mockMvc.perform(put("/api/auth/me").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nomeCompleto", "Invasor"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/auth/me/senha").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("senhaAtual", SENHA, "novaSenha", "NovaSenha2"))))
                .andExpect(status().isUnauthorized());
    }

    private String tokenDe(String email, String senha) throws Exception {
        String corpo = mockMvc.perform(login(email, senha))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private MockHttpServletRequestBuilder login(String email, String senha) throws Exception {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest(email, senha, null)));
    }

    private MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String json(Object corpo) throws Exception {
        return objectMapper.writeValueAsString(corpo);
    }
}
