package com.nexo.service;

import com.nexo.dto.DashboardResponse;
import com.nexo.entity.Perfil;
import com.nexo.security.UsuarioAutenticado;
import org.springframework.stereotype.Component;

/** Seleciona o painel adequado ao perfil, mantendo a decisão fora do controller. */
@Component
public class DashboardFactory {

    private final DashboardColaboradorService colaboradorService;
    private final DashboardGestaoService gestaoService;

    public DashboardFactory(DashboardColaboradorService colaboradorService,
                           DashboardGestaoService gestaoService) {
        this.colaboradorService = colaboradorService;
        this.gestaoService = gestaoService;
    }

    public PainelDashboard para(Perfil papel) {
        return switch (papel) {
            case COLABORADOR -> colaboradorService::gerar;
            case ADMIN, GESTOR -> gestaoService::gerar;
        };
    }

    @FunctionalInterface
    public interface PainelDashboard {
        DashboardResponse gerar(UsuarioAutenticado requisitante);
    }
}
