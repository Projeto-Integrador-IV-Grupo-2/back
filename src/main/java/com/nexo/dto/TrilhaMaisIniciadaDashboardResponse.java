package com.nexo.dto;

import java.util.UUID;

/** Trilha ordenada pela quantidade de eventos reais de início. */
public record TrilhaMaisIniciadaDashboardResponse(UUID trilhaId, String trilhaNome, long totalIniciadas) {
}
