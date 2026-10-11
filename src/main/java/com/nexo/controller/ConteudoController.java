package com.nexo.controller;

import com.nexo.dto.conteudo.ConteudoRequest;
import com.nexo.dto.conteudo.ConteudoResponse;
import com.nexo.repository.ConteudoFiltro;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.ConteudoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Conteúdos de estudo (issue #40).
 *
 * <p>Criar, editar e publicar são de ADMIN e GESTOR (colaborador recebe 403).
 * Leitura é de qualquer usuário autenticado, mas colaborador só enxerga
 * conteúdo publicado — essa regra vive no {@link ConteudoService}.</p>
 */
@RestController
@RequestMapping("/api/conteudos")
public class ConteudoController {

    private static final String GESTAO = "hasAnyRole('ADMIN', 'GESTOR')";

    private final ConteudoService conteudoService;

    public ConteudoController(ConteudoService conteudoService) {
        this.conteudoService = conteudoService;
    }

    @PostMapping
    @PreAuthorize(GESTAO)
    public ResponseEntity<ConteudoResponse> criar(@Valid @RequestBody ConteudoRequest request,
                                                  @AuthenticationPrincipal UsuarioAutenticado usuario,
                                                  UriComponentsBuilder uriBuilder) {
        ConteudoResponse response = conteudoService.criar(request, usuario);
        URI location = uriBuilder.path("/api/conteudos/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    /** Filtros opcionais: {@code titulo}, {@code categoriaId}, {@code areaId}, {@code tipo} e {@code status}. */
    @GetMapping
    public PagedModel<ConteudoResponse> listar(@ModelAttribute ConteudoFiltro filtro,
                                               @PageableDefault(size = 20, sort = "titulo") Pageable pageable,
                                               @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new PagedModel<>(conteudoService.listar(filtro, pageable, usuario));
    }

    @GetMapping("/{id}")
    public ConteudoResponse buscarPorId(@PathVariable UUID id,
                                        @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return conteudoService.buscarPorId(id, usuario);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GESTAO)
    public ConteudoResponse atualizar(@PathVariable UUID id,
                                      @Valid @RequestBody ConteudoRequest request,
                                      @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return conteudoService.atualizar(id, request, usuario);
    }

    @PatchMapping("/{id}/publicar")
    @PreAuthorize(GESTAO)
    public ConteudoResponse publicar(@PathVariable UUID id,
                                     @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return conteudoService.publicar(id, usuario);
    }
}
