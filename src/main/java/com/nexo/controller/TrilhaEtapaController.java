package com.nexo.controller;

import com.nexo.dto.etapa.ConclusaoEtapaResponse;
import com.nexo.dto.etapa.EtapaRequest;
import com.nexo.dto.etapa.EtapaResponse;
import com.nexo.dto.etapa.ReordenarEtapasRequest;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.TrilhaEtapaService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Etapas da trilha e conclusão de etapa (issue #40).
 *
 * <p>Montar a trilha (criar, editar, remover, reordenar) é de ADMIN e GESTOR.
 * Listar e concluir valem para qualquer usuário autenticado do tenant — e a
 * conclusão é sempre do próprio usuário.</p>
 */
@RestController
@RequestMapping("/api/trilhas/{trilhaId}/etapas")
public class TrilhaEtapaController {

    private static final String GESTAO = "hasAnyRole('ADMIN', 'GESTOR')";

    private final TrilhaEtapaService trilhaEtapaService;

    public TrilhaEtapaController(TrilhaEtapaService trilhaEtapaService) {
        this.trilhaEtapaService = trilhaEtapaService;
    }

    @GetMapping
    public List<EtapaResponse> listar(@PathVariable UUID trilhaId,
                                      @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaEtapaService.listar(trilhaId, usuario);
    }

    @PostMapping
    @PreAuthorize(GESTAO)
    public ResponseEntity<EtapaResponse> criar(@PathVariable UUID trilhaId,
                                               @Valid @RequestBody EtapaRequest request,
                                               @AuthenticationPrincipal UsuarioAutenticado usuario,
                                               UriComponentsBuilder uriBuilder) {
        EtapaResponse response = trilhaEtapaService.criar(trilhaId, request, usuario);
        URI location = uriBuilder.path("/api/trilhas/{trilhaId}/etapas/{id}")
                .buildAndExpand(trilhaId, response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{etapaId}")
    @PreAuthorize(GESTAO)
    public EtapaResponse atualizar(@PathVariable UUID trilhaId,
                                   @PathVariable UUID etapaId,
                                   @Valid @RequestBody EtapaRequest request,
                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaEtapaService.atualizar(trilhaId, etapaId, request, usuario);
    }

    @DeleteMapping("/{etapaId}")
    @PreAuthorize(GESTAO)
    public ResponseEntity<Void> remover(@PathVariable UUID trilhaId,
                                        @PathVariable UUID etapaId,
                                        @AuthenticationPrincipal UsuarioAutenticado usuario) {
        trilhaEtapaService.remover(trilhaId, etapaId, usuario);
        return ResponseEntity.noContent().build();
    }

    /** Corpo: {@code {"etapaIds": [...]}} com todas as etapas, na nova sequência. */
    @PatchMapping("/ordem")
    @PreAuthorize(GESTAO)
    public List<EtapaResponse> reordenar(@PathVariable UUID trilhaId,
                                         @Valid @RequestBody ReordenarEtapasRequest request,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaEtapaService.reordenar(trilhaId, request, usuario);
    }

    /** Conclui a etapa para o usuário autenticado e devolve o progresso recalculado. */
    @PostMapping("/{etapaId}/concluir")
    public ConclusaoEtapaResponse concluir(@PathVariable UUID trilhaId,
                                           @PathVariable UUID etapaId,
                                           @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaEtapaService.concluir(trilhaId, etapaId, usuario);
    }
}
