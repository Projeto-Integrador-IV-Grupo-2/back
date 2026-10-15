package com.nexo.controller;

import com.nexo.dto.categoria.CategoriaRequest;
import com.nexo.dto.categoria.CategoriaResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.CategoriaService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Categorias de conteúdo (issue #40). Leitura liberada a qualquer usuário
 * autenticado; criar e editar só ADMIN e GESTOR.
 */
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private static final String GESTAO = "hasAnyRole('ADMIN', 'GESTOR')";

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @PostMapping
    @PreAuthorize(GESTAO)
    public ResponseEntity<CategoriaResponse> criar(@Valid @RequestBody CategoriaRequest request,
                                                   @AuthenticationPrincipal UsuarioAutenticado usuario,
                                                   UriComponentsBuilder uriBuilder) {
        CategoriaResponse response = categoriaService.criar(request, usuario);
        URI location = uriBuilder.path("/api/categorias/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PagedModel<CategoriaResponse> listar(@RequestParam(required = false) String nome,
                                                @RequestParam(defaultValue = "false") boolean incluirInativas,
                                                @PageableDefault(size = 20, sort = "nome") Pageable pageable,
                                                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new PagedModel<>(categoriaService.listar(nome, incluirInativas, pageable, usuario));
    }

    @PutMapping("/{id}")
    @PreAuthorize(GESTAO)
    public CategoriaResponse atualizar(@PathVariable UUID id,
                                       @Valid @RequestBody CategoriaRequest request,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return categoriaService.atualizar(id, request, usuario);
    }
}
