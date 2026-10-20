package com.nexo.controller;

import com.nexo.dto.usuario.AtualizarSituacaoRequest;
import com.nexo.dto.usuario.AtualizarUsuarioRequest;
import com.nexo.dto.usuario.CriarUsuarioRequest;
import com.nexo.dto.usuario.UsuarioResponse;
import com.nexo.repository.UsuarioFiltro;
import com.nexo.service.UsuarioService;
import com.nexo.security.UsuarioAutenticado;
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
 * Endpoints REST de cadastro e gerenciamento de usuários administrativos.
 *
 * <p>Restrito ao perfil Administrador via {@link PreAuthorize}, comparando
 * com {@code com.nexo.entity.Perfil.ADMIN}. Para isso funcionar, o principal
 * autenticado (JWT, validado por {@code JwtAuthenticationFilter}) precisa
 * carregar a authority {@code ROLE_ADMIN}. Um colaborador autenticado sem
 * essa authority recebe HTTP 403 automaticamente (tratado por
 * {@code GlobalExceptionHandler.handleAccessDenied}).</p>
 *
 * <p>Nenhuma regra de negócio vive aqui — tudo é delegado a
 * {@link UsuarioService}; este controller só traduz HTTP &lt;-&gt; DTO.</p>
 */
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody CriarUsuarioRequest request,
                                                  @AuthenticationPrincipal UsuarioAutenticado requisitante,
                                                  UriComponentsBuilder uriBuilder) {
        UsuarioResponse response = usuarioService.criar(request, requisitante);
        URI location = uriBuilder.path("/api/usuarios/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PagedModel<UsuarioResponse> listar(@ModelAttribute UsuarioFiltro filtro,
                                               @PageableDefault(size = 20, sort = "nomeCompleto") Pageable pageable,
                                               @AuthenticationPrincipal UsuarioAutenticado requisitante) {
        return new PagedModel<>(usuarioService.listar(filtro, pageable, requisitante));
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscarPorId(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado requisitante) {
        return usuarioService.buscarPorId(id, requisitante);
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable UUID id,
                                      @Valid @RequestBody AtualizarUsuarioRequest request,
                                      @AuthenticationPrincipal UsuarioAutenticado requisitante) {
        return usuarioService.atualizar(id, request, requisitante);
    }

    @PatchMapping("/{id}/situacao")
    public UsuarioResponse atualizarSituacao(@PathVariable UUID id,
                                              @Valid @RequestBody AtualizarSituacaoRequest request,
                                              @AuthenticationPrincipal UsuarioAutenticado requisitante) {
        return usuarioService.atualizarSituacao(id, request.situacao(), requisitante);
    }
}
