package com.nexo.dto;

import java.math.BigDecimal;
import java.util.List;

/** Indicadores agregados de gestão, limitados ao tenant autenticado. */
public record DashboardGestaoResponse(
        long trilhasAtivas,
        ResumoStatusDashboard atribuicoesPorStatus,
        BigDecimal taxaConclusao,
        List<AreaAtrasoDashboardResponse> areasComMaisAtraso,
        List<TrilhaMaisIniciadaDashboardResponse> trilhasMaisIniciadas) implements DashboardResponse {

    public DashboardGestaoResponse {
        areasComMaisAtraso = List.copyOf(areasComMaisAtraso);
        trilhasMaisIniciadas = List.copyOf(trilhasMaisIniciadas);
    }
}
