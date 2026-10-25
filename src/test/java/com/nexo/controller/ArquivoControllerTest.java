package com.nexo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.event.ArquivoEnviadoEvent;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.service.documento.ArquivosDeTeste;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upload, listagem e download de arquivos (issue #55) de ponta a ponta:
 * contexto Spring completo, PostgreSQL real e login de verdade.
 *
 * <p>O limite de tamanho fica em 64KB aqui, para o teste do limite não
 * precisar montar um arquivo de 10MB. O {@code @Transactional} desfaz o banco
 * ao fim de cada teste, e o {@code ArquivoService} apaga junto os binários
 * gravados na pasta temporária.</p>
 */
@SpringBootTest(properties = "app.documentos.tamanho-maximo=64KB")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class ArquivoControllerTest {

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

    @Autowired
    private ApplicationEvents eventos;

    @Test
    void gestorEnviaPdfEODownloadDevolveONomeOriginal() throws Exception {
        String token = tokenDe(criaUsuario(tenantSeed(), Perfil.GESTOR));
        String documento = criaDocumento(token);
        byte[] pdf = ArquivosDeTeste.pdf("Manual do colaborador");

        String arquivoId = idDe(envia(token, documento, "Relatório anual.pdf", pdf)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/arquivos/")))
                .andExpect(jsonPath("$.nomeOriginal").value("Relatório anual.pdf"))
                .andExpect(jsonPath("$.tipo").value("PDF"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.tamanhoBytes").value(pdf.length))
                .andExpect(jsonPath("$.chaveArmazenamento").doesNotExist()));

        mockMvc.perform(get("/api/conteudos/{id}/arquivos", documento).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(arquivoId));

        mockMvc.perform(get("/api/arquivos/{id}/download", arquivoId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''Relat%C3%B3rio%20anual.pdf")))
                .andExpect(content().bytes(pdf));
        org.assertj.core.api.Assertions.assertThat(eventos.stream(ArquivoEnviadoEvent.class)).hasSize(1);
    }

    @Test
    void aceitaDocxTxtEMd() throws Exception {
        String token = tokenDe(criaUsuario(tenantSeed(), Perfil.ADMIN));
        String documento = criaDocumento(token);

        envia(token, documento, "politica.docx", ArquivosDeTeste.docx("Politica")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DOCX"));
        envia(token, documento, "notas.txt", ArquivosDeTeste.texto("Férias")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("TXT"));
        envia(token, documento, "guia.md", ArquivosDeTeste.texto("# Guia")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("MD"));
    }

    @Test
    void tipoNaoPermitidoOuDisfarcadoResponde400NoCampo() throws Exception {
        String token = tokenDe(criaUsuario(tenantSeed(), Perfil.GESTOR));
        String documento = criaDocumento(token);

        envia(token, documento, "planilha.xlsx", new byte[] {1, 2, 3})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("arquivo"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Envie um arquivo PDF, DOCX, TXT ou MD"));
        // Texto renomeado para .pdf: o tipo é conferido pelo conteúdo.
        envia(token, documento, "falso.pdf", ArquivosDeTeste.texto("não sou PDF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("O conteúdo do arquivo não é um PDF válido"));
    }

    @Test
    void arquivoAcimaDoLimiteResponde400() throws Exception {
        String token = tokenDe(criaUsuario(tenantSeed(), Perfil.GESTOR));
        String documento = criaDocumento(token);

        envia(token, documento, "grande.txt", ArquivosDeTeste.texto("a".repeat(64 * 1024 + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("arquivo"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("O arquivo passa do tamanho máximo de 64 KB"));
    }

    @Test
    void uploadSemOCampoArquivoResponde400() throws Exception {
        String token = tokenDe(criaUsuario(tenantSeed(), Perfil.GESTOR));
        String documento = criaDocumento(token);

        mockMvc.perform(multipart("/api/conteudos/{id}/arquivos", documento)
                        .file(new MockMultipartFile("outro", "a.txt", "text/plain", new byte[] {'a'}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("arquivo"));
    }

    @Test
    void colaboradorRecebe403AoCriarEditarOuEnviarArquivo() throws Exception {
        String tokenGestor = tokenDe(criaUsuario(tenantSeed(), Perfil.GESTOR));
        String documento = criaDocumento(tokenGestor);
        String tokenColaborador = tokenDe(criaUsuario(tenantSeed(), Perfil.COLABORADOR));

        mockMvc.perform(post("/api/conteudos").header(HttpHeaders.AUTHORIZATION, bearer(tokenColaborador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", "X", "tipo", "DOCUMENTO"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/conteudos/{id}", documento)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenColaborador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", "X", "tipo", "DOCUMENTO"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/conteudos/{id}/publicar", documento)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenColaborador)))
                .andExpect(status().isForbidden());
        envia(tokenColaborador, documento, "a.txt", ArquivosDeTeste.texto("oi")).andExpect(status().isForbidden());
    }

    @Test
    void colaboradorSoVeEBaixaDocumentoPublicado() throws Exception {
        String tokenGestor = tokenDe(criaUsuario(tenantSeed(), Perfil.GESTOR));
        String documento = criaDocumento(tokenGestor);
        String arquivo = idDe(envia(tokenGestor, documento, "manual.txt", ArquivosDeTeste.texto("Manual")));
        String tokenColaborador = tokenDe(criaUsuario(tenantSeed(), Perfil.COLABORADOR));

        // Rascunho: some da listagem e o arquivo responde 404.
        mockMvc.perform(get("/api/conteudos").param("size", "200")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenColaborador)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(documento))));
        mockMvc.perform(get("/api/conteudos/{id}/arquivos", documento)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenColaborador)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/arquivos/{id}/download", arquivo)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenColaborador)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/conteudos/{id}/publicar", documento)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenGestor)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conteudos").param("size", "200")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenColaborador)))
                .andExpect(jsonPath("$.content[*].id", hasItem(documento)));
        mockMvc.perform(get("/api/arquivos/{id}/download", arquivo)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenColaborador)))
                .andExpect(status().isOk())
                .andExpect(content().string("Manual"));
    }

    @Test
    void documentoOuArquivoDeOutroTenantResponde404() throws Exception {
        String tokenNosso = tokenDe(criaUsuario(tenantSeed(), Perfil.ADMIN));
        String documento = criaDocumento(tokenNosso);
        String arquivo = idDe(envia(tokenNosso, documento, "manual.txt", ArquivosDeTeste.texto("Manual")));
        mockMvc.perform(patch("/api/conteudos/{id}/publicar", documento)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenNosso)));
        String tokenOutro = tokenDe(criaUsuario(criaTenant(), Perfil.ADMIN));

        mockMvc.perform(get("/api/conteudos/{id}", documento).header(HttpHeaders.AUTHORIZATION, bearer(tokenOutro)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/arquivos/{id}/download", arquivo).header(HttpHeaders.AUTHORIZATION, bearer(tokenOutro)))
                .andExpect(status().isNotFound());
        envia(tokenOutro, documento, "a.txt", ArquivosDeTeste.texto("oi")).andExpect(status().isNotFound());
    }

    @Test
    void downloadExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/arquivos/{id}/download", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions envia(String token, String conteudoId, String nome, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/conteudos/{id}/arquivos", conteudoId)
                .file(new MockMultipartFile("arquivo", nome, "application/octet-stream", bytes))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String criaDocumento(String token) throws Exception {
        return idDe(mockMvc.perform(post("/api/conteudos").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titulo", "Manual " + UUID.randomUUID(), "tipo", "DOCUMENTO"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DOCUMENTO"))
                .andExpect(jsonPath("$.status").value("RASCUNHO")));
    }

    private String idDe(ResultActions resultado) throws Exception {
        String corpo = resultado.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asText();
    }

    private String json(Object corpo) throws Exception {
        return objectMapper.writeValueAsString(corpo);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private Tenant tenantSeed() {
        return tenantRepository.findBySubdominio("nexo").orElseThrow();
    }

    private Tenant criaTenant() {
        Tenant tenant = new Tenant();
        tenant.setNome("Outra Empresa " + UUID.randomUUID());
        tenant.setSubdominio("outra-" + UUID.randomUUID());
        return tenantRepository.saveAndFlush(tenant);
    }

    private Usuario criaUsuario(Tenant tenant, Perfil papel) {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Usuário de Teste " + papel);
        usuario.setEmail("arquivo-" + UUID.randomUUID() + "@nexo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPapel(papel);
        usuario.setAtivo(true);
        return usuarioRepository.saveAndFlush(usuario);
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
