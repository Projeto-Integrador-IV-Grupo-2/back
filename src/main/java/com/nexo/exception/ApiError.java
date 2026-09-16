package com.nexo.exception;

import java.time.Instant;
import java.util.List;

/**
 * Corpo padrão de erro retornado pela API.
 *
 * @param timestamp   momento em que o erro ocorreu
 * @param status      código HTTP
 * @param error       descrição curta do status HTTP
 * @param message     mensagem legível para o cliente
 * @param path        caminho da requisição
 * @param fieldErrors erros de validação por campo (vazio quando não houver)
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldValidationError> fieldErrors) {

    /** Erro de validação associado a um campo específico. */
    public record FieldValidationError(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }
}
