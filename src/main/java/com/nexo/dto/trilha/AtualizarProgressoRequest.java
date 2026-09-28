package com.nexo.dto.trilha;

import com.nexo.entity.StatusAtribuicao;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo da atualização de progresso (Card 07): só o status muda por aqui.
 * Percentual não é um campo de entrada — ver {@link ProgressoTrilhaResponse}.
 */
public record AtualizarProgressoRequest(
        @NotNull(message = "status é obrigatório")
        StatusAtribuicao status) {
}
