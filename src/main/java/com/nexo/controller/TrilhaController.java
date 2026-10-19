package com.nexo.controller;

import com.nexo.dto.trilha.AtribuicaoEmLoteResponse;
import com.nexo.dto.trilha.AtribuirTrilhaRequest;
import com.nexo.dto.trilha.AtualizarProgressoRequest;
import com.nexo.dto.trilha.AtualizarTrilhaRequest;
import com.nexo.dto.trilha.CriarTrilhaRequest;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.dto.trilha.TrilhaDetalheResponse;
import com.nexo.dto.trilha.TrilhaResumoResponse;
import com.nexo.repository.TrilhaFiltro;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.TrilhaAtribuicaoService;
import com.nexo.service.TrilhaService;
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
 * Endpoints de trilhas (Cards 05, 07 e 08).
 *
 * <p>Consulta e progresso não têm restrição de perfil: qualquer usuário
 * autenticado do tenant pode ver as trilhas e mexer no <em>próprio</em>
 * progresso — quem impede ver trilha de outra empresa é o filtro por tenant
 * nos services, e o dono do progresso é sempre o principal autenticado.
 * Cadastrar trilha é exclusivo do perfil ADMIN (mesma regra de
 * {@code PERFIS_CRIAR_TRILHA} no front).</p>
 *
 * <p>Nenhuma regra de negócio vive aqui — tudo é delegado a
 * {@link TrilhaService} e {@link TrilhaAtribuicaoService}; este controller só
 * traduz HTTP &lt;-&gt; DTO.</p>
 */
@RestController
@RequestMapping("/api/trilhas")
public class TrilhaController {

    private final TrilhaService trilhaService;
    private final TrilhaAtribuicaoService trilhaAtribuicaoService;

    public TrilhaController(TrilhaService trilhaService, TrilhaAtribuicaoService trilhaAtribuicaoService) {
        this.trilhaService = trilhaService;
        this.trilhaAtribuicaoService = trilhaAtribuicaoService;
    }

    /**
     * Trilhas ativas do tenant do usuário autenticado, com o progresso dele em
     * cada uma. Filtros opcionais: {@code nome}, {@code areaId}, {@code status}
     * (do progresso do usuário), {@code minhas=true} (aceita {@code sort=prazo})
     * e {@code recomendar=AREA}.
     */
    @GetMapping
    public PagedModel<TrilhaResumoResponse> listar(
            @ModelAttribute TrilhaFiltro filtro,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new PagedModel<>(trilhaService.listar(filtro, pageable, usuario));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TrilhaResumoResponse> criar(@Valid @RequestBody CriarTrilhaRequest request,
                                                      @AuthenticationPrincipal UsuarioAutenticado usuario,
                                                      UriComponentsBuilder uriBuilder) {
        TrilhaResumoResponse response = trilhaService.criar(request, usuario);
        URI location = uriBuilder.path("/api/trilhas/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public TrilhaDetalheResponse detalhe(@PathVariable UUID id,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaService.buscarDetalhe(id, usuario);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TrilhaResumoResponse atualizar(@PathVariable UUID id,
                                          @Valid @RequestBody AtualizarTrilhaRequest request,
                                          @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaService.atualizar(id, request, usuario);
    }

    /** Tira a trilha do catálogo. O progresso de quem já a fazia é preservado. */
    @PatchMapping("/{id}/inativar")
    @PreAuthorize("hasRole('ADMIN')")
    public TrilhaResumoResponse inativar(@PathVariable UUID id,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaService.inativar(id, usuario);
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasRole('ADMIN')")
    public TrilhaResumoResponse ativar(@PathVariable UUID id,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaService.ativar(id, usuario);
    }

    /**
     * Atribui a trilha a colaboradores ou a uma área inteira. Liberado também
     * ao GESTOR: distribuir trilha para a equipe é função dele, diferente de
     * criar ou editar o catálogo, que é só do ADMIN.
     */
    @PostMapping("/{id}/atribuicoes")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public AtribuicaoEmLoteResponse atribuir(@PathVariable UUID id,
                                             @Valid @RequestBody AtribuirTrilhaRequest request,
                                             @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaAtribuicaoService.atribuir(id, request, usuario);
    }

    /** Progresso do usuário autenticado; 404 se ele ainda não iniciou a trilha. */
    @GetMapping("/{id}/progresso")
    public ProgressoTrilhaResponse buscarProgresso(@PathVariable UUID id,
                                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaAtribuicaoService.buscarProgresso(id, usuario);
    }

    /** Inicia a trilha para o usuário autenticado. Idempotente: se já iniciou, devolve o progresso atual. */
    @PostMapping("/{id}/progresso")
    public ProgressoTrilhaResponse iniciar(@PathVariable UUID id,
                                           @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaAtribuicaoService.iniciar(id, usuario);
    }

    @PostMapping("/{id}/progresso/concluir")
    public ProgressoTrilhaResponse concluir(@PathVariable UUID id,
                                            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaAtribuicaoService.concluir(id, usuario);
    }

    @PatchMapping("/{id}/progresso")
    public ProgressoTrilhaResponse atualizarProgresso(@PathVariable UUID id,
                                                      @Valid @RequestBody AtualizarProgressoRequest request,
                                                      @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaAtribuicaoService.atualizar(id, request, usuario);
    }
}
