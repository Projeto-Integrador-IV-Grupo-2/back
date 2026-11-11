package com.nexo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.service.email.EmailMensagem;
import com.nexo.service.email.EnviadorDeEmail;
import com.nexo.service.email.EnviadorRegistro;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Recuperação de senha (issue #76) de ponta a ponta: PostgreSQL real, login
 * de verdade e o enviador de e-mail que só registra o envio (perfil test).
 *
 * <p>Sem {@code @Transactional} de propósito: os e-mails saem depois do
 * commit. Cada teste cria o próprio tenant e usuários, e cada pedido vem de um
 * IP diferente, para o limite por IP de um teste não afetar os outros.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecuperacaoSenhaControllerTest {

    private static final String SENHA_ANTIGA = "SenhaAntiga1";
    private static final String SENHA_NOVA = "SenhaNova2";
    private static final Duration ESPERA = Duration.ofSeconds(10);
    private static final Pattern LINK = Pattern.compile("/redefinir-senha\\?token=([A-Za-z0-9_%-]+)");
    private static final AtomicInteger PROXIMO_IP = new AtomicInteger(1);

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
    private EnviadorDeEmail enviadorDeEmail;

    @Autowired
    private JdbcTemplate jdbc;

    private Tenant tenant;

    @BeforeEach
    void prepara() {
        tenant = new Tenant();
        tenant.setNome("Empresa " + UUID.randomUUID());
        tenant.setSubdominio("senha-" + UUID.randomUUID());
        tenant = tenantRepository.saveAndFlush(tenant);
    }

    @Test
    void fluxoCompletoEsqueciEmailRedefinirLogin() throws Exception {
        Usuario ana = criaUsuario(true);

        esqueci(ana.getEmail(), novoIp()).andExpect(status().isNoContent());
        String token = tokenDoEmail(ana.getEmail());

        redefinir(token, SENHA_NOVA).andExpect(status().isNoContent());

        login(ana.getEmail(), SENHA_NOVA).andExpect(status().isOk());
        login(ana.getEmail(), SENHA_ANTIGA).andExpect(status().isUnauthorized());
        // Aviso de senha alterada e registro no histórico, depois do commit.
        await().atMost(ESPERA).until(() -> emailsPara(ana.getEmail()).stream()
                .anyMatch(email -> email.assunto().contains("foi alterada")));
        await().atMost(ESPERA).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM historico_interacao WHERE usuario_id = ? AND tipo = 'SENHA_REDEFINIDA'",
                Integer.class, ana.getId()) == 1);
        // O banco guarda só o hash: o token em claro não aparece em lugar nenhum dele.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token_redefinicao_senha WHERE token_hash = ?",
                Integer.class, token)).isZero();
    }

    @Test
    void linkJaUsadoInventadoOuExpiradoResponde400() throws Exception {
        Usuario ana = criaUsuario(true);
        esqueci(ana.getEmail(), novoIp()).andExpect(status().isNoContent());
        String token = tokenDoEmail(ana.getEmail());
        redefinir(token, SENHA_NOVA).andExpect(status().isNoContent());

        redefinir(token, "OutraSenha3")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Este link de redefinição é inválido ou expirou. Peça um novo link."));
        redefinir("token-inventado", "OutraSenha3").andExpect(status().isBadRequest());

        Usuario bruno = criaUsuario(true);
        esqueci(bruno.getEmail(), novoIp()).andExpect(status().isNoContent());
        String tokenBruno = tokenDoEmail(bruno.getEmail());
        jdbc.update("UPDATE token_redefinicao_senha SET expira_em = now() - interval '1 minute', "
                + "criado_em = now() - interval '31 minutes' WHERE usuario_id = ?", bruno.getId());
        redefinir(tokenBruno, SENHA_NOVA).andExpect(status().isBadRequest());
        login(bruno.getEmail(), SENHA_ANTIGA).andExpect(status().isOk());
    }

    @Test
    void pedirUmLinkNovoInvalidaOAnterior() throws Exception {
        Usuario ana = criaUsuario(true);
        esqueci(ana.getEmail(), novoIp()).andExpect(status().isNoContent());
        String primeiro = tokenDoEmail(ana.getEmail());

        esqueci(ana.getEmail(), novoIp()).andExpect(status().isNoContent());
        await().atMost(ESPERA).until(() -> linksPara(ana.getEmail()).size() == 2);
        String segundo = linksPara(ana.getEmail()).get(1);

        redefinir(primeiro, SENHA_NOVA).andExpect(status().isBadRequest());
        redefinir(segundo, SENHA_NOVA).andExpect(status().isNoContent());
    }

    @Test
    void esqueciRespondeIgualParaEmailCadastradoOuNao() throws Exception {
        Usuario ana = criaUsuario(true);
        String inexistente = "ninguem-" + UUID.randomUUID() + "@nexo.com";

        esqueci(ana.getEmail(), novoIp()).andExpect(status().isNoContent());
        esqueci(inexistente, novoIp()).andExpect(status().isNoContent());

        tokenDoEmail(ana.getEmail());
        Thread.sleep(300);
        assertThat(emailsPara(inexistente)).isEmpty();
    }

    @Test
    void usuarioInativoNaoRecebeLink() throws Exception {
        Usuario inativo = criaUsuario(false);

        esqueci(inativo.getEmail(), novoIp()).andExpect(status().isNoContent());

        Thread.sleep(500);
        assertThat(emailsPara(inativo.getEmail())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token_redefinicao_senha WHERE usuario_id = ?",
                Integer.class, inativo.getId())).isZero();
    }

    @Test
    void passarDoLimiteDePedidosPorEmailResponde429() throws Exception {
        Usuario ana = criaUsuario(true);
        for (int i = 0; i < 3; i++) {
            esqueci(ana.getEmail(), novoIp()).andExpect(status().isNoContent());
        }

        esqueci(ana.getEmail(), novoIp())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));
    }

    @Test
    void passarDoLimiteDePedidosPorIpResponde429MesmoComEmailsDiferentes() throws Exception {
        String ip = novoIp();
        for (int i = 0; i < 3; i++) {
            esqueci("pessoa-" + UUID.randomUUID() + "@nexo.com", ip).andExpect(status().isNoContent());
        }

        esqueci("pessoa-" + UUID.randomUUID() + "@nexo.com", ip).andExpect(status().isTooManyRequests());
    }

    @Test
    void senhaForaDaRegraResponde400NoCampoSemGastarOLink() throws Exception {
        Usuario ana = criaUsuario(true);
        esqueci(ana.getEmail(), novoIp()).andExpect(status().isNoContent());
        String token = tokenDoEmail(ana.getEmail());

        redefinir(token, "fraca")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItem("novaSenha")));

        redefinir(token, SENHA_NOVA).andExpect(status().isNoContent());
    }

    @Test
    void emailInvalidoResponde400() throws Exception {
        esqueci("nao-e-email", novoIp())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    private ResultActions esqueci(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/esqueci-senha")
                .with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions redefinir(String token, String novaSenha) throws Exception {
        return mockMvc.perform(post("/api/auth/redefinir-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token, "novaSenha", novaSenha))));
    }

    private ResultActions login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, senha))));
    }

    /** Espera o e-mail de redefinição e devolve o token do link. */
    private String tokenDoEmail(String email) {
        await().atMost(ESPERA).until(() -> !linksPara(email).isEmpty());
        List<String> links = linksPara(email);
        return links.get(links.size() - 1);
    }

    private List<String> linksPara(String email) {
        return emailsPara(email).stream()
                .map(mensagem -> LINK.matcher(mensagem.corpo()))
                .filter(Matcher::find)
                .map(encontrado -> URLDecoder.decode(encontrado.group(1), StandardCharsets.UTF_8))
                .toList();
    }

    private List<EmailMensagem> emailsPara(String email) {
        return ((EnviadorRegistro) enviadorDeEmail).enviadosPara(email);
    }

    private Usuario criaUsuario(boolean ativo) {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Pessoa de Teste");
        usuario.setEmail("senha-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA_ANTIGA));
        usuario.setPapel(Perfil.COLABORADOR);
        usuario.setAtivo(ativo);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private static String novoIp() {
        int n = PROXIMO_IP.getAndIncrement();
        return "10.77." + (n / 250) + "." + (n % 250 + 1);
    }
}
