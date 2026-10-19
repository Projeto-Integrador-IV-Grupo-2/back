package com.nexo.controller;

import com.nexo.dto.DashboardResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.DashboardFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Painéis autorizados pelo perfil autenticado, sem lógica de negócio no controller. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardFactory dashboardFactory;

    public DashboardController(DashboardFactory dashboardFactory) {
        this.dashboardFactory = dashboardFactory;
    }

    @GetMapping("/colaborador")
    @PreAuthorize("hasRole('COLABORADOR')")
    public DashboardResponse colaborador(@AuthenticationPrincipal UsuarioAutenticado requisitante) {
        return dashboardFactory.para(requisitante.getPapel()).gerar(requisitante);
    }

    @GetMapping("/gestao")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public DashboardResponse gestao(@AuthenticationPrincipal UsuarioAutenticado requisitante) {
        return dashboardFactory.para(requisitante.getPapel()).gerar(requisitante);
    }
}
