package com.nexo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.config.ClockConfig;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de integração do acompanhamento (Task 5): contexto Spring completo e
 * Postgres real, com login de verdade (o controller usa
 * {@code @AuthenticationPrincipal UsuarioAutenticado}).
 *
 * <p>Cada teste monta um tenant próprio, para as contagens não dependerem de
 * dados de outros testes. {@code @Transactional} desfaz tudo no fim.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AtribuicaoControllerTest {

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
    private AreaRepository areaRepository;

    @Autowired
    private TrilhaRepository trilhaRepository;

    @Autowired
    private TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant tenant;
    private Usuario gestor;
    private Area comercial;
    private Usuario ana;
    private Usuario bruno;
    private Trilha onboarding;
    private Trilha lgpd;

    @BeforeEach
    void montaCenario() {
        tenant = criaTenant();
        gestor = criaUsuario(tenant, Perfil.GESTOR, "Gestora", null);
        comercial = criaArea(tenant, "Comercial");
        Area tecnologia = criaArea(tenant, "Tecnologia");
        ana = criaUsuario(tenant, Perfil.COLABORADOR, "Ana", comercial);
        bruno = criaUsuario(tenant, Perfil.COLABORADOR, "Bruno", tecnologia);
        onboarding = criaTrilha(gestor, "Onboarding");
        lgpd = criaTrilha(gestor, "LGPD");

        atribui(onboarding, ana, StatusAtribuicao.EM_ANDAMENTO, HOJE.minusDays(2));   // atrasada (calculada)
        atribui(lgpd, ana, StatusAtribuicao.CONCLUIDA, HOJE.minusDays(10));           // concluída, nunca atrasa
        atribui(onboarding, bruno, StatusAtribuicao.EM_ANDAMENTO, HOJE.plusDays(5));  // em andamento

        // Ruído de outra empresa: nunca pode aparecer.
        Tenant outro = criaTenant();
        Usuario deFora = criaUsuario(outro, Perfil.COLABORADOR, "Fora", null);
        atribui(criaTrilha(deFora, "Externa"), deFora, StatusAtribuicao.EM_ANDAMENTO, HOJE.minusDays(1));
    }

    @Test
    void colaboradorRecebe403EmTodosOsEndpoints() throws Exception {
        String token = tokenDe(ana);

        mockMvc.perform(get("/api/atribuicoes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/atribuicoes/colaboradores/{id}", ana.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/atribuicoes/exportar").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listagemTrazSoOTenantComStatusEfetivo() throws Exception {
        mockMvc.perform(get("/api/atribuicoes").header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(gestor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                // Ordenação padrão: nome do colaborador.
                .andExpect(jsonPath("$.content[0].colaborador.nomeCompleto").value("Ana"))
                .andExpect(jsonPath("$.content[?(@.trilha.nome == 'Onboarding' && @.colaborador.nomeCompleto == 'Ana')].status")
                        .value("ATRASADA"))
                .andExpect(jsonPath("$.content[?(@.trilha.nome == 'LGPD')].status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.content[?(@.colaborador.nomeCompleto == 'Ana')].area.nome")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Comercial"))));
    }

    @Test
    void filtroAtrasadaPegaPrazoVencidoENaoPegaConcluida() throws Exception {
        mockMvc.perform(get("/api/atribuicoes").param("status", "ATRASADA")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(gestor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].colaborador.nomeCompleto").value("Ana"))
                .andExpect(jsonPath("$.content[0].trilha.nome").value("Onboarding"));

        mockMvc.perform(get("/api/atribuicoes").param("status", "EM_ANDAMENTO")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(gestor))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].colaborador.nomeCompleto").value("Bruno"));
    }

    @Test
    void filtrosCombinamEntreSi() throws Exception {
        mockMvc.perform(get("/api/atribuicoes")
                        .param("areaId", comercial.getId().toString())
                        .param("trilhaId", onboarding.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(gestor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].colaborador.nomeCompleto").value("Ana"));

        mockMvc.perform(get("/api/atribuicoes")
                        .param("usuarioId", bruno.getId().toString())
                        .param("status", "CONCLUIDA")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(gestor))))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void visaoDoColaboradorTrazTotaisQueBatemComALista() throws Exception {
        mockMvc.perform(get("/api/atribuicoes/colaboradores/{id}", ana.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(gestor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colaborador.nomeCompleto").value("Ana"))
                .andExpect(jsonPath("$.area.nome").value("Comercial"))
                .andExpect(jsonPath("$.trilhas.length()").value(2))
                .andExpect(jsonPath("$.totais.total").value(2))
                .andExpect(jsonPath("$.totais.atrasadas").value(1))
                .andExpect(jsonPath("$.totais.concluidas").value(1));
    }

    @Test
    void colaboradorDeOutroTenantRetorna404() throws Exception {
        Usuario deFora = criaUsuario(criaTenant(), Perfil.COLABORADOR, "Outro", null);

        mockMvc.perform(get("/api/atribuicoes/colaboradores/{id}", deFora.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(gestor))))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportaCsvComBomRespeitandoOsFiltros() throws Exception {
        byte[] corpo = mockMvc.perform(get("/api/atribuicoes/exportar")
                        .param("status", "ATRASADA")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(gestor))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("acompanhamento-trilhas.csv")))
                .andReturn().getResponse().getContentAsByteArray();

        String csv = new String(corpo, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿");
        String[] linhas = csv.substring(1).split("\r\n");
        assertThat(linhas).hasSize(2);
        assertThat(linhas[1]).startsWith("Ana;").contains(";Comercial;Onboarding;Atrasada;");
    }

    @Test
    void formatoDeExportacaoDesconhecidoRetorna400() throws Exception {
        mockMvc.perform(get("/api/atribuicoes/exportar").param("formato", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(gestor))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusInexistenteNoFiltroRetorna400() throws Exception {
        mockMvc.perform(get("/api/atribuicoes").param("status", "QUALQUER")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe(gestor))))
                .andExpect(status().isBadRequest());
    }

    private Tenant criaTenant() {
        Tenant novo = new Tenant();
        novo.setNome("Empresa " + UUID.randomUUID());
        novo.setSubdominio("emp-" + UUID.randomUUID());
        return tenantRepository.saveAndFlush(novo);
    }

    private Area criaArea(Tenant dono, String nome) {
        Area area = new Area();
        area.setTenant(dono);
        area.setNome(nome);
        return areaRepository.saveAndFlush(area);
    }

    private Usuario criaUsuario(Tenant dono, Perfil papel, String nome, Area area) {
        Usuario usuario = new Usuario();
        usuario.setTenant(dono);
        usuario.setNomeCompleto(nome);
        usuario.setEmail("acomp-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPapel(papel);
        usuario.setArea(area);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private Trilha criaTrilha(Usuario autor, String nome) {
        Trilha trilha = new Trilha();
        trilha.setTenant(autor.getTenant());
        trilha.setNome(nome);
        trilha.setCriadoPor(autor);
        return trilhaRepository.saveAndFlush(trilha);
    }

    private void atribui(Trilha trilha, Usuario usuario, StatusAtribuicao status, LocalDate prazo) {
        TrilhaAtribuicao atribuicao = new TrilhaAtribuicao();
        atribuicao.setTenant(trilha.getTenant());
        atribuicao.setTrilha(trilha);
        atribuicao.setUsuario(usuario);
        atribuicao.setAtribuidoPor(usuario);
        atribuicao.setStatus(status);
        atribuicao.setPrazo(prazo);
        trilhaAtribuicaoRepository.saveAndFlush(atribuicao);
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
