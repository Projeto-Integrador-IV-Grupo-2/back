package com.nexo.controller;

import com.nexo.dto.area.AreaRequest;
import com.nexo.dto.area.AreaResponse;
import com.nexo.service.AreaService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
 * Endpoints REST do catálogo de Áreas (departamentos).
 *
 * <p><strong>Autorização:</strong> leitura é liberada a qualquer usuário
 * autenticado — o front precisa listar áreas para montar os selects de
 * cadastro de usuário. Já criar, editar e mudar a situação exigem
 * {@code ROLE_ADMIN} ou {@code ROLE_GESTOR} (Card 14 — mesma regra de
 * {@code PERFIS_GESTAO_AREA} no front): um colaborador recebe 403 (tratado
 * por {@code GlobalExceptionHandler.handleAccessDenied}).</p>
 *
 * <p>Nenhuma regra de negócio vive aqui — tudo é delegado ao
 * {@link AreaService}; este controller só traduz HTTP &lt;-&gt; DTO.</p>
 */
@RestController
@RequestMapping("/api/areas")
public class AreaController {

    private static final String GESTAO = "hasAnyRole('ADMIN', 'GESTOR')";

    private final AreaService areaService;

    public AreaController(AreaService areaService) {
        this.areaService = areaService;
    }

    @PostMapping
    @PreAuthorize(GESTAO)
    public ResponseEntity<AreaResponse> criar(@Valid @RequestBody AreaRequest request,
                                              Authentication authentication,
                                              UriComponentsBuilder uriBuilder) {
        AreaResponse response = areaService.criar(request, authentication.getName());
        URI location = uriBuilder.path("/api/areas/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PagedModel<AreaResponse> listar(@RequestParam(required = false) String nome,
                                           @RequestParam(defaultValue = "false") boolean incluirInativas,
                                           @PageableDefault(size = 20, sort = "nome") Pageable pageable,
                                           Authentication authentication) {
        return new PagedModel<>(areaService.listar(nome, incluirInativas, pageable, authentication.getName()));
    }

    @GetMapping("/{id}")
    public AreaResponse buscarPorId(@PathVariable UUID id, Authentication authentication) {
        return areaService.buscarPorId(id, authentication.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize(GESTAO)
    public AreaResponse atualizar(@PathVariable UUID id,
                                  @Valid @RequestBody AreaRequest request,
                                  Authentication authentication) {
        return areaService.atualizar(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/inativar")
    @PreAuthorize(GESTAO)
    public AreaResponse inativar(@PathVariable UUID id, Authentication authentication) {
        return areaService.inativar(id, authentication.getName());
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize(GESTAO)
    public AreaResponse ativar(@PathVariable UUID id, Authentication authentication) {
        return areaService.ativar(id, authentication.getName());
    }
}
