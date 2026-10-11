package com.nexo.dto.etapa;

import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import java.util.UUID;

/**
 * Resposta de {@code POST /etapas/{etapaId}/concluir}: a etapa concluída e o
 * progresso da trilha já recalculado, para o front atualizar a barra sem
 * recarregar.
 */
public record ConclusaoEtapaResponse(UUID etapaId, ProgressoTrilhaResponse progresso) {

    public static ConclusaoEtapaResponse de(UUID etapaId, ProgressoTrilhaResponse progresso) {
        return new ConclusaoEtapaResponse(etapaId, progresso);
    }
}
