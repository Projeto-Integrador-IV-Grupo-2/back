package com.nexo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.area.AreaRequest;
import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de integração do CRUD de Área (Cards 04 e 09): contexto Spring
 * completo e Postgres efêmero via Testcontainers.
 *
 * <p>Usa o administrador seedado por {@code V5__seed_initial_admin.sql} como
 * requisitante, para o tenant ser resolvido de verdade pelo service.
 * {@code @Transactional} reverte os dados ao fim de cada teste.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AreaControllerTest {

    private static final String ADMIN_EMAIL = "admin@nexo.com";
    private static final String COLABORADOR_EMAIL = "colaborador.area@nexo.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant tenant;

    @BeforeEach
    void carregaTenantDoSeed() {
        tenant = usuarioRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getTenant();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void administradorCriaAreaERecebe201ComLocation() throws Exception {
        mockMvc.perform(post("/api/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AreaRequest(nomeUnico(), null))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void nomeEmBrancoRetorna400ComErroPorCampo() throws Exception {
        mockMvc.perform(post("/api/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'nome')]").exists());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void nomeDuplicadoNoMesmoTenantRetorna409() throws Exception {
        String corpo = objectMapper.writeValueAsString(new AreaRequest(nomeUnico(), null));

        mockMvc.perform(post("/api/areas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/areas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void administradorRenomeiaArea() throws Exception {
        Area area = areaGravada(nomeUnico());
        String novoNome = nomeUnico();

        mockMvc.perform(put("/api/areas/{id}", area.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AreaRequest(novoNome, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value(novoNome));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void inativaEDepoisReativaArea() throws Exception {
        Area area = areaGravada(nomeUnico());

        mockMvc.perform(patch("/api/areas/{id}/inativar", area.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(patch("/api/areas/{id}/ativar", area.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void areaComUsuarioVinculadoNaoPodeSerInativada() throws Exception {
        Area area = areaGravada(nomeUnico());
        usuarioVinculadoA(area);

        mockMvc.perform(patch("/api/areas/{id}/inativar", area.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(area.getNome())));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void listagemPadraoOmiteInativas() throws Exception {
        Area inativa = areaGravada(nomeUnico());
        inativa.setAtivo(false);
        areaRepository.saveAndFlush(inativa);

        // Filtra pelo nome para a área ficar na primeira página, seja qual for
        // a quantidade de áreas que outros testes deixaram no banco.
        mockMvc.perform(get("/api/areas").param("nome", inativa.getNome()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + inativa.getId() + "')]").doesNotExist());

        mockMvc.perform(get("/api/areas").param("nome", inativa.getNome()).param("incluirInativas", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + inativa.getId() + "')]").exists())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void criaAreaComDescricaoEDevolveContadores() throws Exception {
        mockMvc.perform(post("/api/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AreaRequest(nomeUnico(), "  Time de produto  "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descricao").value("Time de produto"))
                .andExpect(jsonPath("$.totalPessoas").value(0))
                .andExpect(jsonPath("$.criadoEm").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "gestor.area@nexo.com", roles = "GESTOR")
    void gestorPodeCriarArea() throws Exception {
        criaUsuario("gestor.area@nexo.com", Perfil.GESTOR);

        mockMvc.perform(post("/api/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AreaRequest(nomeUnico(), null))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void ordenacaoPorCampoInexistenteRetorna400() throws Exception {
        mockMvc.perform(get("/api/areas").param("sort", "naoExiste"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void idInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/areas/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    /**
     * Isolamento multi-tenant: mesmo sendo administrador, o requisitante não
     * enxerga nem alcança a área de outra empresa — e recebe 404, não 403,
     * para a API não revelar que aquele registro existe.
     */
    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void areaDeOutroTenantNaoApareceNemPodeSerAlcancada() throws Exception {
        Area deOutraEmpresa = areaDeOutroTenant();

        mockMvc.perform(get("/api/areas").param("incluirInativas", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + deOutraEmpresa.getId() + "')]").doesNotExist());

        mockMvc.perform(get("/api/areas/{id}", deOutraEmpresa.getId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/areas/{id}", deOutraEmpresa.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AreaRequest(nomeUnico(), null))))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/areas/{id}/inativar", deOutraEmpresa.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = COLABORADOR_EMAIL, roles = "COLABORADOR")
    void colaboradorLeAreasMasNaoPodeCriar() throws Exception {
        criaColaborador();

        mockMvc.perform(get("/api/areas"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AreaRequest(nomeUnico(), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = COLABORADOR_EMAIL, roles = "COLABORADOR")
    void colaboradorNaoPodeEditarNemInativarArea() throws Exception {
        criaColaborador();
        Area area = areaGravada(nomeUnico());

        mockMvc.perform(put("/api/areas/{id}", area.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AreaRequest(nomeUnico(), null))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/areas/{id}/inativar", area.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void requisicaoSemAutenticacaoRetorna401() throws Exception {
        mockMvc.perform(get("/api/areas"))
                .andExpect(status().isUnauthorized());
    }

    private Area areaDeOutroTenant() {
        Tenant outraEmpresa = new Tenant();
        outraEmpresa.setNome("Outra Empresa");
        outraEmpresa.setSubdominio("outra-" + UUID.randomUUID().toString().substring(0, 8));
        tenantRepository.saveAndFlush(outraEmpresa);

        Area area = new Area();
        area.setTenant(outraEmpresa);
        area.setNome(nomeUnico());
        area.setAtivo(true);
        return areaRepository.saveAndFlush(area);
    }

    private Area areaGravada(String nome) {
        Area area = new Area();
        area.setTenant(tenant);
        area.setNome(nome);
        area.setAtivo(true);
        return areaRepository.saveAndFlush(area);
    }

    private void usuarioVinculadoA(Area area) {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Vinculado à Área");
        usuario.setEmail("vinculado-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode("senha-de-teste-123"));
        usuario.setPapel(Perfil.COLABORADOR);
        usuario.setArea(area);
        usuario.setDataEntrada(LocalDate.now());
        usuarioRepository.saveAndFlush(usuario);
    }

    private void criaColaborador() {
        if (usuarioRepository.findByEmailIgnoreCase(COLABORADOR_EMAIL).isPresent()) {
            return;
        }
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Colaborador de Teste");
        usuario.setEmail(COLABORADOR_EMAIL);
        usuario.setSenhaHash(passwordEncoder.encode("senha-de-teste-123"));
        usuario.setPapel(Perfil.COLABORADOR);
        usuario.setDataEntrada(LocalDate.now());
        usuarioRepository.saveAndFlush(usuario);
    }

    private void criaUsuario(String email, Perfil papel) {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Usuário de Teste");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode("senha-de-teste-123"));
        usuario.setPapel(papel);
        usuario.setDataEntrada(LocalDate.now());
        usuarioRepository.saveAndFlush(usuario);
    }

    private static String nomeUnico() {
        return "Área " + UUID.randomUUID().toString().substring(0, 8);
    }
}
