package com.nexo.dto.pdi;

import com.nexo.entity.StatusAcaoPdi;
import jakarta.validation.constraints.NotNull;

/** Novo status de uma ação que não é de trilha (ex.: mentoria). */
public record AtualizarStatusAcaoRequest(
        @NotNull(message = "status é obrigatório")
        StatusAcaoPdi status) {
}
