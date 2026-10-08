package com.nexo.dto.pdi;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Competência a desenvolver no PDI. Nível fora de 1 a 5 é erro de validação
 * (400), não regra de negócio.
 *
 * @param competenciaId competência ativa do tenant; aparece uma vez só por PDI
 * @param nivelAtual    nível de hoje, de 1 a 5
 * @param nivelAlvo     nível desejado ao fim do ciclo, de 1 a 5
 * @param observacao    texto livre, opcional
 */
public record PdiItemRequest(
        @NotNull(message = "competência é obrigatória")
        UUID competenciaId,

        @NotNull(message = "nível atual é obrigatório")
        @Min(value = 1, message = "nível atual deve estar entre 1 e 5")
        @Max(value = 5, message = "nível atual deve estar entre 1 e 5")
        Integer nivelAtual,

        @NotNull(message = "nível alvo é obrigatório")
        @Min(value = 1, message = "nível alvo deve estar entre 1 e 5")
        @Max(value = 5, message = "nível alvo deve estar entre 1 e 5")
        Integer nivelAlvo,

        @Size(max = 1000, message = "observação deve ter no máximo 1000 caracteres")
        String observacao) {
}
