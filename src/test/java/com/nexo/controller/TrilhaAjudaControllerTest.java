package com.nexo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.Usuario;
import com.nexo.exception.IaIndisponivelException;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.ClienteIaFake;
import com.nexo.ia.MensagemIa;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ponta a ponta de {@code POST /api/trilhas/sugestoes/ajuda}, com PostgreSQL
 * real e o {@code ClienteIaFake} envolvido por um spy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrilhaAjudaControllerTest {

    private static final String SENHA = "senha-de-teste-123";

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
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private ClienteIa clienteIa;

    private String token;

    @BeforeEach
    void prepara() throws Exception {
        Tenant tenant = criaTenant();
        Area suporte = criaArea(tenant, "Suporte");
        Usuario admin = criaUsuario(tenant, suporte);
        token = tokenDe(admin);
        criaTrilha(admin, "Atendimento ao Cliente", suporte);
    }

    @Test
    void pedidoParaMontarUmaTrilhaDevolveORascunho() throws Exception {
        mockMvc.perform(pedido("Monte uma trilha de LGPD para o time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value(ClienteIaFake.TEXTO_RASCUNHO_SIMULADO))
                .andExpect(jsonPath("$.rascunho.nome").value("Trilha: Monte uma trilha de LGPD para o time"))
                .andExpect(jsonPath("$.rascunho.prazoDias").value(30))
                .andExpect(jsonPath("$.rascunho.objetivos").isNotEmpty());
    }

    @Test
    void outroPedidoDevolveSoOTexto() throws Exception {
        mockMvc.perform(pedido("O que devo estudar primeiro?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value(ClienteIaFake.PREFIXO_RESPOSTA
                        + " para: O que devo estudar primeiro?"))
                .andExpect(jsonPath("$.rascunho").doesNotExist());
    }

    @Test
    @SuppressWarnings("unchecked")
    void oModeloRecebeOCatalogoDoTenantEOPedido() throws Exception {
        mockMvc.perform(pedido("O que devo estudar?")).andExpect(status().isOk());

        ArgumentCaptor<List<MensagemIa>> mensagens = ArgumentCaptor.forClass(List.class);
        verify(clienteIa).gerarResposta(anyString(), mensagens.capture());
        String turno = mensagens.getValue().get(0).texto();
        assertThat(turno)
                .contains("Atendimento ao Cliente")
                .contains("<pergunta>\nO que devo estudar?\n</pergunta>")
                .doesNotContain("Usuário de Teste");
    }

    @Test
    void pedidoVazioResponde400() throws Exception {
        mockMvc.perform(pedido("  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(post("/api/trilhas/sugestoes/ajuda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"oi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void comIaForaDoArResponde503() throws Exception {
        doThrow(new IaIndisponivelException("O assistente de IA está indisponível no momento."))
                .when(clienteIa).gerarResposta(anyString(), anyList());

        mockMvc.perform(pedido("Monte uma trilha"))
                .andExpect(status().isServiceUnavailable());
    }

    private MockHttpServletRequestBuilder pedido(String pergunta) throws Exception {
        return post("/api/trilhas/sugestoes/ajuda")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pergunta", pergunta)));
    }

    private Tenant criaTenant() {
        Tenant novo = new Tenant();
        novo.setNome("Empresa " + UUID.randomUUID());
        novo.setSubdominio("empresa-" + UUID.randomUUID());
        return tenantRepository.saveAndFlush(novo);
    }

    private Area criaArea(Tenant dono, String nome) {
        Area area = new Area();
        area.setTenant(dono);
        area.setNome(nome);
        return areaRepository.saveAndFlush(area);
    }

    private Usuario criaUsuario(Tenant dono, Area area) {
        Usuario usuario = new Usuario();
        usuario.setTenant(dono);
        usuario.setNomeCompleto("Usuário de Teste");
        usuario.setEmail("ajuda-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPapel(Perfil.ADMIN);
        usuario.setArea(area);
        usuario.setAtivo(true);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private Trilha criaTrilha(Usuario criadoPor, String nome, Area area) {
        Trilha trilha = new Trilha();
        trilha.setTenant(criadoPor.getTenant());
        trilha.setNome(nome);
        trilha.setArea(area);
        trilha.setCriadoPor(criadoPor);
        return trilhaRepository.saveAndFlush(trilha);
    }

    private String tokenDe(Usuario usuario) throws Exception {
        String corpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(usuario.getEmail(), SENHA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }
}
