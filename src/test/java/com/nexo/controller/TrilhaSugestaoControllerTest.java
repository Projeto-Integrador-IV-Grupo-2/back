package com.nexo.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.dto.trilha.SugestoesDeTrilhaResponse;
import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.exception.IaIndisponivelException;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.ClienteIaFake;
import com.nexo.ia.RespostaIa;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.service.recomendacao.CacheDeSugestoes;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ponta a ponta de {@code GET /api/trilhas/sugestoes} (issue #63), com
 * PostgreSQL real e o {@code ClienteIaFake} envolvido por um spy — o fake
 * responde texto livre, e aqui o modelo precisa devolver JSON com ids.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrilhaSugestaoControllerTest {

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
    private TrilhaAtribuicaoRepository atribuicaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CacheDeSugestoes cache;

    @MockitoSpyBean
    private ClienteIa clienteIa;

    private Tenant tenant;
    private Usuario colaborador;
    private String token;
    private Trilha atendimento;
    private Trilha lgpd;
    private Trilha concluida;

    @BeforeEach
    void prepara() throws Exception {
        tenant = criaTenant();
        Area suporte = criaArea(tenant, "Suporte");
        colaborador = criaUsuario(tenant, suporte);
        token = tokenDe(colaborador);
        atendimento = criaTrilha(colaborador, "Atendimento ao Cliente", suporte);
        lgpd = criaTrilha(colaborador, "Fundamentos de LGPD", null);
        concluida = criaTrilha(colaborador, "Onboarding", suporte);
        criaProgresso(concluida, colaborador, StatusAtribuicao.CONCLUIDA);
        // Cada teste começa sem nada guardado: o cache é um bean único da aplicação.
        cache.invalidar(tenant.getId(), colaborador.getId());
    }

    /**
     * Sem {@code modeloResponde}: quem responde é o {@link ClienteIaFake} de
     * verdade, que lê o catálogo do prompt e devolve o JSON de sugestões. O
     * caminho feliz passa pelo fluxo inteiro, prompt incluído.
     */
    @Test
    void devolveAsTrilhasIndicadasPelaIaComOMotivoDeCadaUma() throws Exception {
        mockMvc.perform(comToken(get("/api/trilhas/sugestoes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geradoPorIa").value(true))
                .andExpect(jsonPath("$.aviso").doesNotExist())
                // Só as não concluídas entram no catálogo, em ordem de nome.
                .andExpect(jsonPath("$.sugestoes", hasSize(2)))
                .andExpect(jsonPath("$.sugestoes[0].id").value(atendimento.getId().toString()))
                .andExpect(jsonPath("$.sugestoes[0].nome").value("Atendimento ao Cliente"))
                .andExpect(jsonPath("$.sugestoes[0].area.nome").value("Suporte"))
                .andExpect(jsonPath("$.sugestoes[0].motivo").value(ClienteIaFake.MOTIVO_SIMULADO))
                .andExpect(jsonPath("$.sugestoes[1].id").value(lgpd.getId().toString()));
    }

    @Test
    void aTrilhaJaConcluidaNaoEntraNaSugestao() throws Exception {
        mockMvc.perform(comToken(get("/api/trilhas/sugestoes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugestoes[*].id")
                        .value(not(hasItem(concluida.getId().toString()))));
    }

    @Test
    void idInventadoPeloModeloNuncaChegaNaResposta() throws Exception {
        Trilha deOutraEmpresa = trilhaDeOutroTenant();
        modeloResponde("""
                {"sugestoes":[
                  {"id":"%s","motivo":"Inventada."},
                  {"id":"%s","motivo":"De outra empresa."},
                  {"id":"%s","motivo":"Já concluída."},
                  {"id":"%s","motivo":"Boa para você."}
                ]}""".formatted(UUID.randomUUID(), deOutraEmpresa.getId(), concluida.getId(), lgpd.getId()));

        mockMvc.perform(comToken(get("/api/trilhas/sugestoes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugestoes", hasSize(1)))
                .andExpect(jsonPath("$.sugestoes[0].id").value(lgpd.getId().toString()));
    }

    @Test
    void comIaForaDoArResponde200ComARecomendacaoPorAreaEOAviso() throws Exception {
        doThrow(new IaIndisponivelException("O assistente de IA está indisponível no momento."))
                .when(clienteIa).gerarResposta(anyString(), anyList());

        mockMvc.perform(comToken(get("/api/trilhas/sugestoes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geradoPorIa").value(false))
                .andExpect(jsonPath("$.aviso").value(SugestoesDeTrilhaResponse.AVISO_SEM_IA))
                // Trilha da área do usuário primeiro; a concluída fica de fora.
                .andExpect(jsonPath("$.sugestoes", hasSize(2)))
                .andExpect(jsonPath("$.sugestoes[0].id").value(atendimento.getId().toString()))
                .andExpect(jsonPath("$.sugestoes[1].id").value(lgpd.getId().toString()));
    }

    @Test
    void doisAcessosSeguidosChamamOModeloUmaVezSo() throws Exception {
        mockMvc.perform(comToken(get("/api/trilhas/sugestoes"))).andExpect(status().isOk());
        mockMvc.perform(comToken(get("/api/trilhas/sugestoes"))).andExpect(status().isOk());

        verify(clienteIa, times(1)).gerarResposta(anyString(), anyList());
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/trilhas/sugestoes")).andExpect(status().isUnauthorized());
    }

    private void modeloResponde(String json) {
        doReturn(new RespostaIa(json, 0, 0)).when(clienteIa).gerarResposta(anyString(), anyList());
    }

    private MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private Trilha trilhaDeOutroTenant() {
        Tenant outro = criaTenant();
        return criaTrilha(criaUsuario(outro, null), "Trilha de outra empresa", null);
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
        usuario.setEmail("sugestao-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPapel(Perfil.COLABORADOR);
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

    private void criaProgresso(Trilha trilha, Usuario usuario, StatusAtribuicao status) {
        TrilhaAtribuicao progresso = new TrilhaAtribuicao();
        progresso.setTenant(trilha.getTenant());
        progresso.setTrilha(trilha);
        progresso.setUsuario(usuario);
        progresso.setAtribuidoPor(usuario);
        progresso.setStatus(status);
        atribuicaoRepository.saveAndFlush(progresso);
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
