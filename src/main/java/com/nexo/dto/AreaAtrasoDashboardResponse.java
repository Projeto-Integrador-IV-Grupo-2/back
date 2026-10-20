package com.nexo.dto;

import java.util.UUID;

/** Área do colaborador com atribuições atrasadas no tenant. */
public record AreaAtrasoDashboardResponse(UUID areaId, String areaNome, long atribuicoesAtrasadas) {
}
