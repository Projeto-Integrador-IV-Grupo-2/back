package com.nexo.dto.pdi;

import com.nexo.entity.StatusPdi;
import jakarta.validation.constraints.NotNull;

/** Novo status de um PDI. */
public record AtualizarStatusPdiRequest(
        @NotNull(message = "status é obrigatório")
        StatusPdi status) {
}
