package com.nexo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
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
 * Fluxo de autenticação de ponta a ponta: login, uso do token e rotas protegidas.
 *
 * <p>Os casos gerais criam o próprio usuário, para não depender do seed; o
 * administrador inicial tem um teste à parte, que trava a senha documentada no
 * README.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String NOME = "Usuário de Teste";
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
    private PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void criaUsuarioAtivo() {
        email = novoEmail();
        criaUsuario(email, SENHA, true);
    }

    @Test
    void loginComCredenciaisValidasDevolveToken() throws Exception {
        mockMvc.perform(login(email, SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEm").isNotEmpty())
                .andExpect(jsonPath("$.usuario.email").value(email))
                .andExpect(jsonPath("$.usuario.papel").value("COLABORADOR"))
                .andExpect(jsonPath("$.usuario.tenantId").isNotEmpty());
    }

    @Test
    void administradorInicialEntraComASenhaDocumentada() throws Exception {
        mockMvc.perform(login("admin@nexo.com", "admin123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.papel").value("ADMINISTRADOR"));
    }

    @Test
    void loginComSenhaErradaDevolve401() throws Exception {
        mockMvc.perform(login(email, "senha-errada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    void loginComEmailInexistenteDevolve401() throws Exception {
        mockMvc.perform(login(novoEmail(), SENHA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginSemEmailDevolve400() throws Exception {
        mockMvc.perform(login("", SENHA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void loginDeUsuarioInativoDevolve403() throws Exception {
        String emailInativo = novoEmail();
        criaUsuario(emailInativo, SENHA, false);

        mockMvc.perform(login(emailInativo, SENHA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Usuário inativo"));
    }

    @Test
    void rotaProtegidaSemTokenDevolve401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Autenticação necessária"));
    }

    @Test
    void rotaProtegidaComTokenInvalidoDevolve401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaProtegidaComTokenValidoDevolveUsuarioLogado() throws Exception {
        String token = autenticaEObtemToken();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nomeCompleto").value(NOME))
                .andExpect(jsonPath("$.papel").value("COLABORADOR"));
    }

    private String autenticaEObtemToken() throws Exception {
        String corpo = mockMvc.perform(login(email, SENHA))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private MockHttpServletRequestBuilder login(String email, String senha) throws Exception {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, senha)));
    }

    private void criaUsuario(String email, String senha, boolean ativo) {
        Tenant tenant = tenantRepository.findBySubdominio("nexo").orElseThrow();
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto(NOME);
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setPapel(Perfil.COLABORADOR);
        usuario.setAtivo(ativo);
        usuarioRepository.saveAndFlush(usuario);
    }

    private static String novoEmail() {
        return "teste-" + UUID.randomUUID() + "@nexo.com";
    }
}
