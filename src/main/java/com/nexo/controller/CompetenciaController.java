package com.nexo.controller;

import com.nexo.dto.competencia.CompetenciaRequest;
import com.nexo.dto.competencia.CompetenciaResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.CompetenciaService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Endpoints do catálogo de competências (issue #46). Todo o recurso é de
 * ADMIN e GESTOR — quem monta PDIs; um colaborador recebe 403.
 *
 * <p>Nenhuma regra de negócio vive aqui — tudo é delegado ao
 * {@link CompetenciaService}.</p>
 */
@RestController
@RequestMapping("/api/competencias")
@PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
public class CompetenciaController {

    private final CompetenciaService competenciaService;

    public CompetenciaController(CompetenciaService competenciaService) {
        this.competenciaService = competenciaService;
    }

    @PostMapping
    public ResponseEntity<CompetenciaResponse> criar(@Valid @RequestBody CompetenciaRequest request,
                                                     @AuthenticationPrincipal UsuarioAutenticado usuario,
                                                     UriComponentsBuilder uriBuilder) {
        CompetenciaResponse response = competenciaService.criar(request, usuario);
        URI location = uriBuilder.path("/api/competencias/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PagedModel<CompetenciaResponse> listar(@RequestParam(required = false) String nome,
                                                  @RequestParam(defaultValue = "false") boolean incluirInativas,
                                                  @PageableDefault(size = 20, sort = "nome") Pageable pageable,
                                                  @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new PagedModel<>(competenciaService.listar(nome, incluirInativas, pageable, usuario));
    }

    @GetMapping("/{id}")
    public CompetenciaResponse buscarPorId(@PathVariable UUID id,
                                           @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return competenciaService.buscarPorId(id, usuario);
    }

    @PutMapping("/{id}")
    public CompetenciaResponse atualizar(@PathVariable UUID id,
                                         @Valid @RequestBody CompetenciaRequest request,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return competenciaService.atualizar(id, request, usuario);
    }

    @PatchMapping("/{id}/inativar")
    public CompetenciaResponse inativar(@PathVariable UUID id,
                                        @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return competenciaService.inativar(id, usuario);
    }

    @PatchMapping("/{id}/ativar")
    public CompetenciaResponse ativar(@PathVariable UUID id,
                                      @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return competenciaService.ativar(id, usuario);
    }
}
