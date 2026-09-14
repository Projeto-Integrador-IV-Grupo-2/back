package com.nexo.exception;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/**
 * Corpo padrão de erro retornado pela API.
 *
 * <p>Construído pelo builder do Lombok: {@code timestamp} e {@code fieldErrors}
 * têm valor padrão, então só os campos relevantes ao erro precisam ser informados.</p>
 *
 * @param timestamp   momento em que o erro ocorreu (padrão: agora)
 * @param status      código HTTP
 * @param error       descrição curta do status HTTP
 * @param message     mensagem legível para o cliente
 * @param path        caminho da requisição
 * @param fieldErrors erros de validação por campo (padrão: lista vazia)
 */
@Builder
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldValidationError> fieldErrors) {

    public ApiError {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    /** Erro de validação associado a um campo específico. */
    public record FieldValidationError(String field, String message) {
    }
}
