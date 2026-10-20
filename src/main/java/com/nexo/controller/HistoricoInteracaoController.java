package com.nexo.controller;

import com.nexo.dto.HistoricoInteracaoResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.HistoricoInteracaoService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de leitura do histórico, com autorização por perfil e tenant no service. */
@RestController
@RequestMapping("/api/historico")
public class HistoricoInteracaoController {

    private final HistoricoInteracaoService historicoService;

    public HistoricoInteracaoController(HistoricoInteracaoService historicoService) {
        this.historicoService = historicoService;
    }

    @GetMapping
    public PagedModel<HistoricoInteracaoResponse> listar(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado requisitante) {
        return new PagedModel<>(historicoService.listar(pageable, requisitante));
    }
}
