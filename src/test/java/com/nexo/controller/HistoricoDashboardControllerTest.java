package com.nexo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.entity.HistoricoInteracao;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoInteracaoHistorico;
import com.nexo.entity.Trilha;
import com.nexo.entity.Usuario;
import com.nexo.repository.HistoricoInteracaoRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.PageRequest;

/** Integra eventos, persistência, leitura paginada e isolamento pelo usuário autenticado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HistoricoDashboardControllerTest {

    private static final String SENHA = "senha-de-teste-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TrilhaRepository trilhaRepository;
    @Autowired private TrilhaAtribuicaoRepository atribuicaoRepository;
    @Autowired private HistoricoInteracaoRepository historicoRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Tenant tenant;
    private Usuario colaborador;
    private Trilha trilha;

    @Test
    void acessosInicioEConclusaoSaoRegistradosEExpostosNoHistorico() throws Exception {
        prepararRecursos();
        String token = tokenDe(colaborador);

        mockMvc.perform(get("/api/trilhas/{id}", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));

        mockMvc.perform(post("/api/trilhas/{id}/progresso/concluir", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));

        List<HistoricoInteracao> historicos = historicoRepository
                .findAllByTenantIdAndUsuarioId(tenant.getId(), colaborador.getId(), PageRequest.of(0, 20))
                .getContent();
        assertThat(historicos).extracting(HistoricoInteracao::getTipo).containsExactlyInAnyOrder(
                TipoInteracaoHistorico.TRILHA_ACESSADA,
                TipoInteracaoHistorico.TRILHA_INICIADA,
                TipoInteracaoHistorico.TRILHA_CONCLUIDA);

        mockMvc.perform(get("/api/historico")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/dashboard/colaborador")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trilhasPorStatus.total").value(1))
                .andExpect(jsonPath("$.trilhasPorStatus.concluidas").value(1))
                .andExpect(jsonPath("$.percentualMedio").value(100.0));

        mockMvc.perform(get("/api/dashboard/gestao")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @AfterEach
    void limparDadosDoTeste() {
        if (tenant == null || colaborador == null) {
            return;
        }
        historicoRepository.findAllByTenantIdAndUsuarioId(
                        tenant.getId(), colaborador.getId(), PageRequest.of(0, 100))
                .getContent()
                .forEach(historicoRepository::delete);
        atribuicaoRepository.findByTenantIdAndUsuarioId(tenant.getId(), colaborador.getId())
                .forEach(atribuicaoRepository::delete);
        if (trilha != null) {
            trilhaRepository.delete(trilha);
        }
        usuarioRepository.delete(colaborador);
        tenantRepository.delete(tenant);
    }

    private void prepararRecursos() {
        String sufixo = UUID.randomUUID().toString();
        tenant = new Tenant();
        tenant.setNome("Tenant Histórico " + sufixo);
        tenant.setSubdominio("historico-" + sufixo.substring(0, 8));
        tenant = tenantRepository.saveAndFlush(tenant);

        colaborador = new Usuario();
        colaborador.setTenant(tenant);
        colaborador.setNomeCompleto("Colaborador Histórico");
        colaborador.setEmail("historico-" + sufixo + "@empresa.com");
        colaborador.setSenhaHash(passwordEncoder.encode(SENHA));
        colaborador.setPapel(Perfil.COLABORADOR);
        colaborador.setAtivo(true);
        colaborador = usuarioRepository.saveAndFlush(colaborador);

        trilha = new Trilha();
        trilha.setTenant(tenant);
        trilha.setNome("Trilha para auditoria");
        trilha.setCriadoPor(colaborador);
        trilha.setAtivo(true);
        trilha = trilhaRepository.saveAndFlush(trilha);
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
