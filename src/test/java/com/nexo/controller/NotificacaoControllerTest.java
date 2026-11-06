package com.nexo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.config.ClockConfig;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.dto.trilha.AtribuirTrilhaRequest;
import com.nexo.entity.Notificacao;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoNotificacao;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.repository.NotificacaoRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.service.notificacao.GeradorDeNotificacoes;
import com.nexo.service.notificacao.NotificacaoFactory;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes de integração das notificações (issue #78): contexto Spring completo,
 * Postgres real e login de verdade.
 *
 * <p><strong>Sem {@code @Transactional}</strong> de propósito: a geração roda
 * num listener {@code @Async} depois do commit, então o teste precisa que a
 * atribuição seja commitada de verdade e espera o resultado com Awaitility.
 * Cada teste cria um tenant próprio e só conta notificações dos próprios
 * usuários, para não depender do que os outros testes deixaram no banco.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificacaoControllerTest {

    private static final String SENHA = "senha-de-teste-123";
    private static final LocalDate HOJE = LocalDate.now(ClockConfig.FUSO);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TrilhaRepository trilhaRepository;

    @Autowired
    private TrilhaAtribuicaoRepository atribuicaoRepository;

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @Autowired
    private GeradorDeNotificacoes gerador;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final NotificacaoFactory factory = new NotificacaoFactory();

    private Tenant tenant;
    private Usuario gestor;
    private Usuario ana;
    private Usuario bruno;
    private Trilha trilha;

    @BeforeEach
    void montaCenario() {
        tenant = criaTenant();
        gestor = criaUsuario(tenant, Perfil.GESTOR, "Gestora");
        ana = criaUsuario(tenant, Perfil.COLABORADOR, "Ana");
        bruno = criaUsuario(tenant, Perfil.COLABORADOR, "Bruno");
        trilha = criaTrilha(gestor, "Segurança da informação");
    }

    @Test
    void atribuirTrilhaGeraANotificacaoComLinkParaATrilha() throws Exception {
        mockMvc.perform(post("/api/trilhas/{id}/atribuicoes", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(gestor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AtribuirTrilhaRequest(List.of(ana.getId()), null, HOJE.plusDays(10)))))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(totalDe(ana)).isEqualTo(1));

        String token = tokenDe(ana);
        mockMvc.perform(get("/api/notificacoes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("TRILHA_ATRIBUIDA"))
                .andExpect(jsonPath("$.content[0].titulo").value("Nova trilha para você"))
                .andExpect(jsonPath("$.content[0].link").value("/trilhas/" + trilha.getId()))
                .andExpect(jsonPath("$.content[0].lida").value(false));
        mockMvc.perform(get("/api/notificacoes/nao-lidas/total").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        assertThat(totalDe(bruno)).isZero();
    }

    @Test
    void contadorBateComAListaEMarcarLidaDiminui() throws Exception {
        Notificacao antiga = salva(factory.trilhaAtribuida(ana, trilha, null));
        Notificacao nova = salva(factory.trilhaInativada(ana, trilha));
        String token = tokenDe(ana);

        mockMvc.perform(get("/api/notificacoes").param("naoLidas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                // Mais recentes primeiro.
                .andExpect(jsonPath("$.content[0].id").value(nova.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(antiga.getId().toString()));

        mockMvc.perform(patch("/api/notificacoes/{id}/lida", antiga.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lida").value(true))
                .andExpect(jsonPath("$.lidaEm").isNotEmpty());
        // Marcar de novo não muda nada (idempotente).
        mockMvc.perform(patch("/api/notificacoes/{id}/lida", antiga.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notificacoes/nao-lidas/total").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/notificacoes").param("naoLidas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(nova.getId().toString()));
        mockMvc.perform(get("/api/notificacoes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void marcarTodasZeraOContadorSoDoUsuarioLogado() throws Exception {
        salva(factory.trilhaAtribuida(ana, trilha, null));
        salva(factory.trilhaInativada(ana, trilha));
        salva(factory.trilhaInativada(bruno, trilha));
        String token = tokenDe(ana);

        mockMvc.perform(patch("/api/notificacoes/lidas").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notificacoes/nao-lidas/total").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.total").value(0));
        assertThat(totalDe(bruno)).isEqualTo(1);
    }

    @Test
    void notificacaoDeOutraPessoaOuDeOutroTenantResponde404() throws Exception {
        Notificacao doBruno = salva(factory.trilhaInativada(bruno, trilha));
        Tenant outro = criaTenant();
        Usuario deFora = criaUsuario(outro, Perfil.COLABORADOR, "Fora");
        Notificacao deOutroTenant = salva(factory.trilhaInativada(deFora, criaTrilha(deFora, "Externa")));
        String token = tokenDe(ana);

        mockMvc.perform(patch("/api/notificacoes/{id}/lida", doBruno.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/notificacoes/{id}/lida", deOutroTenant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/notificacoes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        assertThat(notificacaoRepository.findById(doBruno.getId()).orElseThrow().isLida()).isFalse();
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/notificacoes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notificacoes/nao-lidas/total")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamanhoDaPaginaTemLimite() throws Exception {
        salva(factory.trilhaInativada(ana, trilha));

        mockMvc.perform(get("/api/notificacoes").param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(ana))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(50));
    }

    @Test
    void rotinaDePrazoProximoNaoDuplicaAoRodarDuasVezes() {
        atribui(ana, HOJE.plusDays(2), StatusAtribuicao.EM_ANDAMENTO);      // avisa
        atribui(bruno, HOJE.plusDays(20), StatusAtribuicao.NAO_INICIADA);   // longe demais
        Trilha concluida = criaTrilha(gestor, "Concluída");
        TrilhaAtribuicao jaConcluida = novaAtribuicao(concluida, bruno, HOJE.plusDays(1), StatusAtribuicao.CONCLUIDA);
        atribuicaoRepository.saveAndFlush(jaConcluida);                    // concluída não avisa

        gerador.avisarPrazosProximos();
        gerador.avisarPrazosProximos();

        List<Notificacao> daAna = notificacoesDe(ana);
        assertThat(daAna).hasSize(1);
        assertThat(daAna.get(0).getTipo()).isEqualTo(TipoNotificacao.PRAZO_PROXIMO);
        assertThat(daAna.get(0).getMensagem()).contains("vence em 2 dias");
        assertThat(notificacoesDe(bruno)).isEmpty();
    }

    @Test
    void limpezaApagaSoAsLidasHaMaisDe90Dias() {
        Notificacao velha = factory.trilhaInativada(ana, trilha);
        velha.marcarLida(OffsetDateTime.now().minusDays(91));
        Notificacao recente = factory.trilhaInativada(ana, trilha);
        recente.marcarLida(OffsetDateTime.now().minusDays(10));
        Notificacao naoLida = factory.trilhaAtribuida(ana, trilha, null);
        salva(velha);
        salva(recente);
        salva(naoLida);

        gerador.apagarLidasAntigas();

        assertThat(notificacoesDe(ana)).extracting(Notificacao::getId)
                .containsExactlyInAnyOrder(recente.getId(), naoLida.getId());
    }

    private long totalDe(Usuario usuario) {
        return notificacaoRepository.countByTenantIdAndUsuarioIdAndLidaEmIsNull(tenant.getId(), usuario.getId());
    }

    private List<Notificacao> notificacoesDe(Usuario usuario) {
        return notificacaoRepository.findByTenantIdAndUsuarioIdOrderByCriadaEmDescIdDesc(
                tenant.getId(), usuario.getId(), Pageable.unpaged()).getContent();
    }

    private Notificacao salva(Notificacao notificacao) {
        return notificacaoRepository.saveAndFlush(notificacao);
    }

    private void atribui(Usuario usuario, LocalDate prazo, StatusAtribuicao status) {
        atribuicaoRepository.saveAndFlush(novaAtribuicao(trilha, usuario, prazo, status));
    }

    private TrilhaAtribuicao novaAtribuicao(Trilha daTrilha, Usuario usuario, LocalDate prazo,
                                            StatusAtribuicao status) {
        TrilhaAtribuicao atribuicao = new TrilhaAtribuicao();
        atribuicao.setTenant(daTrilha.getTenant());
        atribuicao.setTrilha(daTrilha);
        atribuicao.setUsuario(usuario);
        atribuicao.setAtribuidoPor(gestor);
        atribuicao.setStatus(status);
        atribuicao.setPrazo(prazo);
        return atribuicao;
    }

    private Tenant criaTenant() {
        Tenant novo = new Tenant();
        novo.setNome("Empresa " + UUID.randomUUID());
        novo.setSubdominio("notif-" + UUID.randomUUID());
        return tenantRepository.saveAndFlush(novo);
    }

    private Usuario criaUsuario(Tenant dono, Perfil papel, String nome) {
        Usuario usuario = new Usuario();
        usuario.setTenant(dono);
        usuario.setNomeCompleto(nome);
        usuario.setEmail("notif-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPapel(papel);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private Trilha criaTrilha(Usuario autor, String nome) {
        Trilha nova = new Trilha();
        nova.setTenant(autor.getTenant());
        nova.setNome(nome);
        nova.setCriadoPor(autor);
        return trilhaRepository.saveAndFlush(nova);
    }

    private String tokenDe(Usuario usuario) throws Exception {
        String corpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(usuario.getEmail(), SENHA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
