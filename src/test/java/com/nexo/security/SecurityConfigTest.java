package com.nexo.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Valida as regras da cadeia de segurança: rotas públicas, rotas protegidas,
 * restrição das rotas administrativas ao perfil ADMINISTRADOR e CORS do frontend.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final String SENHA = "senha-de-teste-123";
    private static final String ROTA_ADMINISTRATIVA = "/api/admin/usuarios";
    private static final String ORIGEM_DO_FRONT = "http://localhost:5173";

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

    @Test
    void healthEhPublico() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void rotaDesconhecidaSemTokenDevolve401() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Autenticação necessária"));
    }

    @Test
    void colaboradorRecebe403EmRotaAdministrativa() throws Exception {
        String token = tokenDe(Perfil.COLABORADOR);

        mockMvc.perform(get(ROTA_ADMINISTRATIVA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Acesso negado"));
    }

    @Test
    void adminNaoEhBarradoEmRotaAdministrativa() throws Exception {
        String token = tokenDe(Perfil.ADMINISTRADOR);

        // 404 (e não 403): a autorização passou, só não existe controller nesse caminho ainda.
        mockMvc.perform(get(ROTA_ADMINISTRATIVA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rotaAdministrativaSemTokenDevolve401() throws Exception {
        mockMvc.perform(get(ROTA_ADMINISTRATIVA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preflightDeCorsLiberaAOrigemDoFrontend() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, ORIGEM_DO_FRONT)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_DO_FRONT))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflightDeOrigemNaoConfiguradaEhRejeitado() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://origem-nao-autorizada.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginNaoCriaSessaoNoServidor() throws Exception {
        String email = criaUsuario(Perfil.COLABORADOR);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, SENHA))))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    if (result.getRequest().getSession(false) != null) {
                        throw new AssertionError("A API não deve criar sessão no servidor");
                    }
                });
    }

    private String tokenDe(Perfil papel) throws Exception {
        String email = criaUsuario(papel);
        String corpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, SENHA))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private String criaUsuario(Perfil papel) {
        Tenant tenant = tenantRepository.findBySubdominio("nexo").orElseThrow();
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Usuário de Teste");
        usuario.setEmail("seguranca-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPapel(papel);
        usuario.setAtivo(true);
        return usuarioRepository.saveAndFlush(usuario).getEmail();
    }
}
