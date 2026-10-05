package com.nexo.controller;

import com.nexo.dto.atribuicao.AtribuicaoAcompanhamentoResponse;
import com.nexo.dto.atribuicao.ColaboradorAcompanhamentoResponse;
import com.nexo.repository.AtribuicaoFiltro;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AcompanhamentoService;
import com.nexo.service.exportacao.ArquivoExportado;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acompanhamento das trilhas dos colaboradores (Task 5): consulta paginada,
 * visão por colaborador e exportação.
 *
 * <p>Restrito a ADMIN e GESTOR; um colaborador recebe 403. Filtros opcionais
 * em todos os endpoints de lista: {@code trilhaId}, {@code areaId} (área do
 * colaborador), {@code usuarioId} e {@code status}.</p>
 *
 * <p>Nenhuma regra de negócio vive aqui — tudo é delegado ao
 * {@link AcompanhamentoService}; este controller só traduz HTTP &lt;-&gt; DTO.</p>
 */
@RestController
@RequestMapping("/api/atribuicoes")
@PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
public class AtribuicaoController {

    private final AcompanhamentoService acompanhamentoService;

    public AtribuicaoController(AcompanhamentoService acompanhamentoService) {
        this.acompanhamentoService = acompanhamentoService;
    }

    @GetMapping
    public PagedModel<AtribuicaoAcompanhamentoResponse> listar(
            @ModelAttribute AtribuicaoFiltro filtro,
            @PageableDefault(size = 20, sort = "usuario.nomeCompleto") Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new PagedModel<>(acompanhamentoService.listar(filtro, pageable, usuario));
    }

    @GetMapping("/colaboradores/{usuarioId}")
    public ColaboradorAcompanhamentoResponse colaborador(@PathVariable UUID usuarioId,
                                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return acompanhamentoService.colaborador(usuarioId, usuario);
    }

    /** Baixa o acompanhamento com os mesmos filtros da listagem. Hoje só {@code formato=csv}. */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@ModelAttribute AtribuicaoFiltro filtro,
                                           @RequestParam(defaultValue = "csv") String formato,
                                           @SortDefault(sort = "usuario.nomeCompleto") Sort sort,
                                           @AuthenticationPrincipal UsuarioAutenticado usuario) {
        ArquivoExportado arquivo = acompanhamentoService.exportar(filtro, sort, formato, usuario);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                .body(arquivo.conteudo());
    }
}
