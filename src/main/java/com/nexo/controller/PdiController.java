package com.nexo.controller;

import com.nexo.dto.pdi.AtualizarStatusAcaoRequest;
import com.nexo.dto.pdi.AtualizarStatusPdiRequest;
import com.nexo.dto.pdi.ColaboradorOpcaoResponse;
import com.nexo.dto.pdi.CriarPdiRequest;
import com.nexo.dto.pdi.PdiAcaoRequest;
import com.nexo.dto.pdi.PdiAcaoResponse;
import com.nexo.dto.pdi.PdiDetalheResponse;
import com.nexo.dto.pdi.PdiItemRequest;
import com.nexo.dto.pdi.PdiItemResponse;
import com.nexo.dto.pdi.PdiResumoResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.PdiAcaoService;
import com.nexo.service.PdiService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Endpoints do PDI (issue #46).
 *
 * <p>Consultar (listar, detalhar, "meu PDI") é liberado a qualquer usuário
 * autenticado — o {@link PdiService} recorta o que cada perfil vê. Criar,
 * mudar status e incluir itens/ações é de ADMIN e GESTOR; além do perfil, o
 * service exige que o GESTOR seja o gestor daquele PDI.</p>
 *
 * <p>Nenhuma regra de negócio vive aqui — este controller só traduz HTTP
 * &lt;-&gt; DTO.</p>
 */
@RestController
@RequestMapping("/api/pdis")
public class PdiController {

    private static final String GESTAO = "hasAnyRole('ADMIN', 'GESTOR')";

    private final PdiService pdiService;
    private final PdiAcaoService pdiAcaoService;

    public PdiController(PdiService pdiService, PdiAcaoService pdiAcaoService) {
        this.pdiService = pdiService;
        this.pdiAcaoService = pdiAcaoService;
    }

    @PostMapping
    @PreAuthorize(GESTAO)
    public ResponseEntity<PdiDetalheResponse> criar(@Valid @RequestBody CriarPdiRequest request,
                                                    @AuthenticationPrincipal UsuarioAutenticado usuario,
                                                    UriComponentsBuilder uriBuilder) {
        PdiDetalheResponse response = pdiService.criar(request, usuario);
        URI location = uriBuilder.path("/api/pdis/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PagedModel<PdiResumoResponse> listar(
            @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new PagedModel<>(pdiService.listar(pageable, usuario));
    }

    /** Opções do campo "Colaborador" ao criar um PDI (usuários ativos do tenant). */
    @GetMapping("/colaboradores")
    @PreAuthorize(GESTAO)
    public PagedModel<ColaboradorOpcaoResponse> colaboradores(
            @RequestParam(required = false) String nome,
            @PageableDefault(size = 20, sort = "nomeCompleto") Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new PagedModel<>(pdiService.listarColaboradores(nome, pageable, usuario));
    }

    /** PDI ativo do usuário autenticado; 404 se ele não tiver. */
    @GetMapping("/meu")
    public PdiDetalheResponse meu(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return pdiService.meu(usuario);
    }

    @GetMapping("/{id}")
    public PdiDetalheResponse detalhar(@PathVariable UUID id,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return pdiService.detalhar(id, usuario);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(GESTAO)
    public PdiDetalheResponse mudarStatus(@PathVariable UUID id,
                                          @Valid @RequestBody AtualizarStatusPdiRequest request,
                                          @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return pdiService.mudarStatus(id, request, usuario);
    }

    @PostMapping("/{id}/itens")
    @PreAuthorize(GESTAO)
    @ResponseStatus(HttpStatus.CREATED)
    public PdiItemResponse adicionarItem(@PathVariable UUID id,
                                         @Valid @RequestBody PdiItemRequest request,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return pdiService.adicionarItem(id, request, usuario);
    }

    @PostMapping("/{id}/itens/{itemId}/acoes")
    @PreAuthorize(GESTAO)
    @ResponseStatus(HttpStatus.CREATED)
    public PdiAcaoResponse adicionarAcao(@PathVariable UUID id,
                                         @PathVariable UUID itemId,
                                         @Valid @RequestBody PdiAcaoRequest request,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return pdiAcaoService.adicionar(id, itemId, request, usuario);
    }

    /** Status manual — só para ações que não são de trilha. */
    @PatchMapping("/{id}/itens/{itemId}/acoes/{acaoId}/status")
    @PreAuthorize(GESTAO)
    public PdiAcaoResponse atualizarStatusAcao(@PathVariable UUID id,
                                               @PathVariable UUID itemId,
                                               @PathVariable UUID acaoId,
                                               @Valid @RequestBody AtualizarStatusAcaoRequest request,
                                               @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return pdiAcaoService.atualizarStatus(id, itemId, acaoId, request, usuario);
    }
}
