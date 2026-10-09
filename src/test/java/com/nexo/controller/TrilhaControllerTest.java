package com.nexo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.trilha.AtribuirTrilhaRequest;
import com.nexo.dto.trilha.AtualizarTrilhaRequest;
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
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de integração de ponta a ponta (contexto Spring completo + Postgres
 * efêmero via Testcontainers, igual a {@code UsuarioControllerTest}).
 *
 * <p>Autentica via login de verdade ({@code POST /api/auth/login}), não
 * {@code @WithMockUser}: o controller usa {@code @AuthenticationPrincipal
 * UsuarioAutenticado} (para ler o tenant sem consulta extra — ver
 * {@code TrilhaService}), e só o filtro JWT real popula esse tipo de
 * principal. {@code @WithMockUser} usaria um {@code UserDetails} genérico,
 * que nunca é um {@code UsuarioAutenticado} e quebraria o endpoint.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrilhaControllerTest {

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
    private TrilhaRepository trilhaRepository;

    @Autowired
    private TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AreaRepository areaRepository;

    @Test
    void retorna200ComTrilhaEProgressoQuandoAmbosExistem() throws Exception {
        Usuario usuario = criaUsuario(tenantSeed());
        Trilha trilha = criaTrilha(usuario);
        criaProgresso(trilha, usuario, StatusAtribuicao.EM_ANDAMENTO);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas/{id}", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trilha.getId().toString()))
                .andExpect(jsonPath("$.nome").value(trilha.getNome()))
                .andExpect(jsonPath("$.progresso.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.progresso.percentual").value(50));
    }

    @Test
    void retornaProgressoNaoIniciadoQuandoAplicavel() throws Exception {
        Usuario usuario = criaUsuario(tenantSeed());
        Trilha trilha = criaTrilha(usuario);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas/{id}", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progresso.status").value("NAO_INICIADA"))
                .andExpect(jsonPath("$.progresso.percentual").value(0))
                .andExpect(jsonPath("$.progresso.id").doesNotExist());
    }

    @Test
    void listagemVaziaRetorna200ComPaginaSemConteudo() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void listagemTrazAsTrilhasAtivasDoTenant() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        Trilha trilha = criaTrilha(usuario);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(trilha.getId().toString()))
                .andExpect(jsonPath("$.content[0].nome").value(trilha.getNome()))
                // Resumo: o cartão mostra o progresso do usuário; descrição só no detalhe.
                .andExpect(jsonPath("$.content[0].progresso.status").value("NAO_INICIADA"))
                .andExpect(jsonPath("$.content[0].descricao").doesNotExist());
    }

    @Test
    void listagemTrazOProgressoDoUsuarioQuandoEleJaIniciou() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        Trilha trilha = criaTrilha(usuario);
        criaProgresso(trilha, usuario, StatusAtribuicao.CONCLUIDA);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].progresso.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.content[0].progresso.percentual").value(100));
    }

    @Test
    void administradorCriaTrilhaERecebe201() throws Exception {
        Usuario admin = criaUsuario(criaTenant(), Perfil.ADMIN);
        String token = tokenDe(admin);

        mockMvc.perform(post("/api/trilhas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Onboarding\",\"areaId\":null,\"prazoDias\":30,\"objetivos\":\"Conhecer a empresa\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.nome").value("Onboarding"))
                .andExpect(jsonPath("$.objetivos").value("Conhecer a empresa"))
                .andExpect(jsonPath("$.progresso.status").value("NAO_INICIADA"));

        mockMvc.perform(post("/api/trilhas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"onboarding\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void colaboradorNaoPodeCriarTrilha() throws Exception {
        String token = tokenDe(criaUsuario(tenantSeed()));

        mockMvc.perform(post("/api/trilhas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Qualquer\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void criarTrilhaSemNomeRetorna400() throws Exception {
        String token = tokenDe(criaUsuario(criaTenant(), Perfil.ADMIN));

        mockMvc.perform(post("/api/trilhas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\" \",\"prazoDias\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'nome')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'prazoDias')]").exists());
    }

    @Test
    void usuarioIniciaEConcluiAPropriaTrilha() throws Exception {
        Usuario usuario = criaUsuario(tenantSeed());
        Trilha trilha = criaTrilha(usuario);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        mockMvc.perform(patch("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONCLUIDA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.percentual").value(100))
                .andExpect(jsonPath("$.concluidoEm").isNotEmpty());

        mockMvc.perform(patch("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NAO_INICIADA\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void atualizarProgressoNaoIniciadoRetorna404() throws Exception {
        Usuario usuario = criaUsuario(tenantSeed());
        Trilha trilha = criaTrilha(usuario);
        String token = tokenDe(usuario);

        mockMvc.perform(patch("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONCLUIDA\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listagemOmiteTrilhaInativa() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        Trilha inativa = criaTrilha(usuario);
        inativa.setAtivo(false);
        trilhaRepository.saveAndFlush(inativa);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void listagemNaoVazaTrilhaDeOutroTenant() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        Trilha deOutraEmpresa = criaTrilha(criaUsuario(criaTenant()));
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.content[?(@.id == '" + deOutraEmpresa.getId() + "')]").doesNotExist());
    }

    @Test
    void listagemPagina() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        criaTrilha(usuario);
        criaTrilha(usuario);
        criaTrilha(usuario);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas")
                        .param("size", "2")
                        .param("page", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void listagemExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/trilhas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retorna404ParaTrilhaInexistente() throws Exception {
        Usuario usuario = criaUsuario(tenantSeed());
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna404ParaTrilhaDeOutroTenant() throws Exception {
        Usuario usuario = criaUsuario(tenantSeed());
        Tenant outroTenant = criaTenant();
        Usuario criadorDeOutroTenant = criaUsuario(outroTenant);
        Trilha trilhaDeOutroTenant = criaTrilha(criadorDeOutroTenant);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas/{id}", trilhaDeOutroTenant.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void exigeAutenticacao() throws Exception {
        Trilha trilha = criaTrilha(criaUsuario(tenantSeed()));

        mockMvc.perform(get("/api/trilhas/{id}", trilha.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ---- Task 5: busca, filtros, "minhas trilhas" e trilhas por área ----

    @Test
    void filtraPorTrechoDoNomeSemDiferenciarMaiusculas() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        criaTrilha(usuario, "Excelência em Vendas", null);
        criaTrilha(usuario, "Segurança da Informação", null);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas").param("nome", "VENDAS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Excelência em Vendas"));
    }

    @Test
    void filtroNaoIniciadaTrazAsTrilhasSemProgressoDoUsuario() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        Trilha iniciada = criaTrilha(usuario, "Iniciada", null);
        Trilha nova = criaTrilha(usuario, "Nova", null);
        criaProgresso(iniciada, usuario, StatusAtribuicao.EM_ANDAMENTO);
        // Progresso de outra pessoa na trilha "Nova" não conta como iniciada para o usuário.
        Usuario colega = criaUsuario(usuario.getTenant());
        criaProgresso(nova, colega, StatusAtribuicao.EM_ANDAMENTO);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas").param("status", "NAO_INICIADA")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Nova"));

        mockMvc.perform(get("/api/trilhas").param("status", "EM_ANDAMENTO")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Iniciada"));
    }

    @Test
    void filtroAtrasadaConsideraPrazoVencidoEOsFiltrosCombinam() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        Area vendas = criaArea(usuario.getTenant(), "Vendas");
        Trilha vencidaNaArea = criaTrilha(usuario, "Vencida na área", vendas);
        Trilha vencidaForaDaArea = criaTrilha(usuario, "Vencida fora", null);
        criaProgresso(vencidaNaArea, usuario, StatusAtribuicao.EM_ANDAMENTO, LocalDate.now().minusDays(1));
        criaProgresso(vencidaForaDaArea, usuario, StatusAtribuicao.EM_ANDAMENTO, LocalDate.now().minusDays(1));
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas")
                        .param("status", "ATRASADA")
                        .param("areaId", vendas.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Vencida na área"))
                .andExpect(jsonPath("$.content[0].progresso.status").value("ATRASADA"));
    }

    @Test
    void minhasTrilhasOrdenadasPorPrazoComSemPrazoNoFim() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        Trilha semPrazo = criaTrilha(usuario, "A - sem prazo", null);
        Trilha prazoLonge = criaTrilha(usuario, "B - prazo longe", null);
        Trilha prazoPerto = criaTrilha(usuario, "C - prazo perto", null);
        criaTrilha(usuario, "D - nunca iniciada", null);
        criaProgresso(semPrazo, usuario, StatusAtribuicao.EM_ANDAMENTO, null);
        criaProgresso(prazoLonge, usuario, StatusAtribuicao.EM_ANDAMENTO, LocalDate.now().plusDays(30));
        criaProgresso(prazoPerto, usuario, StatusAtribuicao.EM_ANDAMENTO, LocalDate.now().plusDays(2));
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas").param("minhas", "true").param("sort", "prazo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].nome").value("C - prazo perto"))
                .andExpect(jsonPath("$.content[1].nome").value("B - prazo longe"))
                .andExpect(jsonPath("$.content[2].nome").value("A - sem prazo"));

        // A trilha iniciada continua aparecendo no catálogo completo.
        mockMvc.perform(get("/api/trilhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.page.totalElements").value(4));
    }

    @Test
    void trilhasDaAreaTrazSoAsDaquelaArea() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        Area vendas = criaArea(usuario.getTenant(), "Vendas");
        criaTrilha(usuario, "Da área", vendas);
        criaTrilha(usuario, "Sem área", null);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/areas/{id}/trilhas", vendas.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Da área"));
    }

    @Test
    void trilhasDeAreaDeOutroTenantRetornam404() throws Exception {
        Usuario usuario = criaUsuario(criaTenant());
        Area deOutraEmpresa = criaArea(criaTenant(), "Externa");
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/areas/{id}/trilhas", deOutraEmpresa.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void recomendacaoPorAreaColocaAsTrilhasDaAreaDoUsuarioPrimeiro() throws Exception {
        Tenant tenant = criaTenant();
        Area vendas = criaArea(tenant, "Vendas");
        Usuario usuario = criaUsuario(tenant);
        usuario.setArea(vendas);
        usuarioRepository.saveAndFlush(usuario);
        criaTrilha(usuario, "A - outra área", null);
        criaTrilha(usuario, "Z - da minha área", vendas);
        String token = tokenDe(usuario);

        mockMvc.perform(get("/api/trilhas").param("recomendar", "AREA")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value("Z - da minha área"))
                .andExpect(jsonPath("$.content[1].nome").value("A - outra área"));

        // Sem recomendação, vale a ordem por nome.
        mockMvc.perform(get("/api/trilhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.content[0].nome").value("A - outra área"));
    }

    @Test
    void ordenarOCatalogoPorPrazoRetorna400() throws Exception {
        String token = tokenDe(criaUsuario(criaTenant()));

        mockMvc.perform(get("/api/trilhas").param("sort", "prazo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private Area criaArea(Tenant tenant, String nome) {
        Area area = new Area();
        area.setTenant(tenant);
        area.setNome(nome);
        return areaRepository.saveAndFlush(area);
    }

    private Trilha criaTrilha(Usuario criadoPor, String nome, Area area) {
        Trilha trilha = new Trilha();
        trilha.setTenant(criadoPor.getTenant());
        trilha.setNome(nome);
        trilha.setArea(area);
        trilha.setCriadoPor(criadoPor);
        return trilhaRepository.saveAndFlush(trilha);
    }

    private void criaProgresso(Trilha trilha, Usuario usuario, StatusAtribuicao status, LocalDate prazo) {
        TrilhaAtribuicao progresso = new TrilhaAtribuicao();
        progresso.setTenant(trilha.getTenant());
        progresso.setTrilha(trilha);
        progresso.setUsuario(usuario);
        progresso.setAtribuidoPor(usuario);
        progresso.setStatus(status);
        progresso.setPrazo(prazo);
        trilhaAtribuicaoRepository.saveAndFlush(progresso);
    }

    private Tenant tenantSeed() {
        return tenantRepository.findBySubdominio("nexo").orElseThrow();
    }

    // ---- Card 42: gestão de trilhas --------------------------------------

    @Test
    void administradorEditaTrilha() throws Exception {
        Usuario admin = criaUsuario(tenantSeed(), Perfil.ADMIN);
        Trilha trilha = criaTrilha(admin);
        String token = tokenDe(admin);
        String novoNome = "Trilha renomeada " + UUID.randomUUID();

        mockMvc.perform(put("/api/trilhas/{id}", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AtualizarTrilhaRequest(novoNome, null, 15, "Objetivos novos", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value(novoNome));
    }

    @Test
    void edicaoComNomeDeOutraTrilhaRetorna409() throws Exception {
        Usuario admin = criaUsuario(tenantSeed(), Perfil.ADMIN);
        Trilha existente = criaTrilha(admin);
        Trilha emEdicao = criaTrilha(admin);
        String token = tokenDe(admin);

        mockMvc.perform(put("/api/trilhas/{id}", emEdicao.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AtualizarTrilhaRequest(existente.getNome(), null, null, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void trilhaInativaSaiDaListagemMasOProgressoEhMantido() throws Exception {
        Usuario admin = criaUsuario(tenantSeed(), Perfil.ADMIN);
        Trilha trilha = criaTrilha(admin);
        criaProgresso(trilha, admin, StatusAtribuicao.EM_ANDAMENTO);
        String token = tokenDe(admin);

        mockMvc.perform(patch("/api/trilhas/{id}/inativar", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/trilhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + trilha.getId() + "')]").doesNotExist());

        // O progresso continua lá: inativar esconde do catálogo, não apaga histórico.
        mockMvc.perform(get("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
    }

    @Test
    void colaboradorNaoPodeEditarInativarNemAtribuir() throws Exception {
        Usuario admin = criaUsuario(tenantSeed(), Perfil.ADMIN);
        Trilha trilha = criaTrilha(admin);
        Usuario colaborador = criaUsuario(tenantSeed());
        String token = tokenDe(colaborador);

        mockMvc.perform(put("/api/trilhas/{id}", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AtualizarTrilhaRequest("Qualquer", null, null, null, null))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/trilhas/{id}/inativar", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/trilhas/{id}/atribuicoes", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AtribuirTrilhaRequest(List.of(colaborador.getId()), null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    /**
     * Regressão: a atribuição criada pelo gestor nasce {@code NAO_INICIADA}, e
     * "iniciar trilha" respondia 200 deixando-a parada nesse status.
     */
    void colaboradorIniciaTrilhaQueOGestorAtribuiu() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        Trilha trilha = criaTrilha(gestor);
        Usuario colaborador = criaUsuario(tenantSeed());
        String tokenGestor = tokenDe(gestor);

        mockMvc.perform(post("/api/trilhas/{id}/atribuicoes", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AtribuirTrilhaRequest(List.of(colaborador.getId()), null, null))))
                .andExpect(status().isOk());

        String tokenColaborador = tokenDe(colaborador);
        mockMvc.perform(get("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_INICIADA"));

        mockMvc.perform(post("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenColaborador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));

        // E persistiu de verdade, não só na resposta.
        mockMvc.perform(get("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenColaborador))
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
    }

    @Test
    void iniciarNaoReabreTrilhaJaConcluida() throws Exception {
        Usuario colaborador = criaUsuario(tenantSeed());
        Trilha trilha = criaTrilha(colaborador);
        criaProgresso(trilha, colaborador, StatusAtribuicao.CONCLUIDA);
        String token = tokenDe(colaborador);

        mockMvc.perform(post("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
    }

    @Test
    void atribuirParaAreaInexistenteRetorna404() throws Exception {
        Usuario admin = criaUsuario(tenantSeed(), Perfil.ADMIN);
        Trilha trilha = criaTrilha(admin);
        String token = tokenDe(admin);

        mockMvc.perform(post("/api/trilhas/{id}/atribuicoes", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AtribuirTrilhaRequest(null, UUID.randomUUID(), null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioIdsComNuloRetorna400EmVezDe500() throws Exception {
        Usuario admin = criaUsuario(tenantSeed(), Perfil.ADMIN);
        Trilha trilha = criaTrilha(admin);
        String token = tokenDe(admin);

        mockMvc.perform(post("/api/trilhas/{id}/atribuicoes", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioIds\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gestorAtribuiTrilhaEReatribuirNaoDuplica() throws Exception {
        Usuario gestor = criaUsuario(tenantSeed(), Perfil.GESTOR);
        Trilha trilha = criaTrilha(gestor);
        Usuario colaborador = criaUsuario(tenantSeed());
        String token = tokenDe(gestor);
        String corpo = objectMapper.writeValueAsString(
                new AtribuirTrilhaRequest(List.of(colaborador.getId()), null, LocalDate.now().plusDays(10)));

        mockMvc.perform(post("/api/trilhas/{id}/atribuicoes", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atribuidos").value(1))
                .andExpect(jsonPath("$.ignorados").value(0));

        mockMvc.perform(post("/api/trilhas/{id}/atribuicoes", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atribuidos").value(0))
                .andExpect(jsonPath("$.ignorados").value(1));
    }

    @Test
    void atribuicaoSemUsuarioNemAreaRetorna400() throws Exception {
        Usuario admin = criaUsuario(tenantSeed(), Perfil.ADMIN);
        Trilha trilha = criaTrilha(admin);
        String token = tokenDe(admin);

        mockMvc.perform(post("/api/trilhas/{id}/atribuicoes", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AtribuirTrilhaRequest(null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void prazoVencidoApareceComoAtrasadaSemEsperarARotina() throws Exception {
        Usuario admin = criaUsuario(tenantSeed(), Perfil.ADMIN);
        Trilha trilha = criaTrilha(admin);
        TrilhaAtribuicao vencida = criaProgresso(trilha, admin, StatusAtribuicao.EM_ANDAMENTO);
        vencida.setPrazo(LocalDate.now().minusDays(1));
        trilhaAtribuicaoRepository.saveAndFlush(vencida);
        String token = tokenDe(admin);

        mockMvc.perform(get("/api/trilhas/{id}/progresso", trilha.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATRASADA"));
    }

    private Tenant criaTenant() {
        Tenant tenant = new Tenant();
        tenant.setNome("Outra Empresa " + UUID.randomUUID());
        tenant.setSubdominio("outra-" + UUID.randomUUID());
        return tenantRepository.saveAndFlush(tenant);
    }

    private Usuario criaUsuario(Tenant tenant) {
        return criaUsuario(tenant, Perfil.COLABORADOR);
    }

    private Usuario criaUsuario(Tenant tenant, Perfil papel) {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Usuário de Teste");
        usuario.setEmail("trilha-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPapel(papel);
        usuario.setAtivo(true);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private Trilha criaTrilha(Usuario criadoPor) {
        Trilha trilha = new Trilha();
        trilha.setTenant(criadoPor.getTenant());
        trilha.setNome("Trilha de teste " + UUID.randomUUID());
        trilha.setCriadoPor(criadoPor);
        return trilhaRepository.saveAndFlush(trilha);
    }

    private TrilhaAtribuicao criaProgresso(Trilha trilha, Usuario usuario, StatusAtribuicao status) {
        TrilhaAtribuicao progresso = new TrilhaAtribuicao();
        progresso.setTenant(trilha.getTenant());
        progresso.setTrilha(trilha);
        progresso.setUsuario(usuario);
        progresso.setAtribuidoPor(usuario);
        progresso.setStatus(status);
        return trilhaAtribuicaoRepository.saveAndFlush(progresso);
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
