package com.nexo.dto;

import java.math.BigDecimal;
import java.util.List;

/** Dados necessários para o painel do próprio colaborador. */
public record DashboardColaboradorResponse(
        ResumoStatusDashboard trilhasPorStatus,
        List<ProximoPrazoDashboardResponse> proximosPrazos,
        BigDecimal percentualMedio,
        List<HistoricoInteracaoResponse> ultimasAtividades) implements DashboardResponse {

    public DashboardColaboradorResponse {
        proximosPrazos = List.copyOf(proximosPrazos);
        ultimasAtividades = List.copyOf(ultimasAtividades);
    }
}
