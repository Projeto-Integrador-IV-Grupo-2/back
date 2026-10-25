package com.nexo.controller;

import com.nexo.dto.arquivo.ArquivoResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.documento.ArquivoParaDownload;
import com.nexo.service.documento.ArquivoService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Arquivos dos documentos (issue #55): upload, listagem e download.
 *
 * <p>Enviar arquivo é de ADMIN e GESTOR (colaborador recebe 403). Listar e
 * baixar seguem a visibilidade do conteúdo, decidida no
 * {@link ArquivoService}: colaborador só alcança arquivos de conteúdo
 * publicado.</p>
 *
 * <p>Nenhuma regra de negócio vive aqui — este controller só traduz HTTP
 * &lt;-&gt; DTO.</p>
 */
@RestController
public class ArquivoController {

    private static final String GESTAO = "hasAnyRole('ADMIN', 'GESTOR')";

    private final ArquivoService arquivoService;

    public ArquivoController(ArquivoService arquivoService) {
        this.arquivoService = arquivoService;
    }

    /** Multipart com o arquivo no campo {@code arquivo}. */
    @PostMapping(path = "/api/conteudos/{id}/arquivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(GESTAO)
    public ResponseEntity<ArquivoResponse> enviar(@PathVariable UUID id,
                                                  @RequestPart("arquivo") MultipartFile arquivo,
                                                  @AuthenticationPrincipal UsuarioAutenticado usuario,
                                                  UriComponentsBuilder uriBuilder) {
        ArquivoResponse response = arquivoService.enviar(id, arquivo, usuario);
        URI location = uriBuilder.path("/api/arquivos/{id}/download").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/api/conteudos/{id}/arquivos")
    public List<ArquivoResponse> listar(@PathVariable UUID id,
                                        @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return arquivoService.listar(id, usuario);
    }

    /** Devolve o arquivo como anexo, com o nome original. */
    @GetMapping("/api/arquivos/{id}/download")
    public ResponseEntity<InputStreamResource> baixar(@PathVariable UUID id,
                                                      @AuthenticationPrincipal UsuarioAutenticado usuario) {
        ArquivoParaDownload download = arquivoService.baixar(id, usuario);
        ContentDisposition anexo = ContentDisposition.attachment()
                .filename(download.nomeOriginal(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, anexo.toString())
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.tamanhoBytes())
                .body(new InputStreamResource(download.conteudo()));
    }
}
