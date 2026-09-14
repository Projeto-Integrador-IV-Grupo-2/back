package com.nexo.exception;

import lombok.experimental.StandardException;

/**
 * Lançada quando um recurso solicitado não existe.
 * Mapeada para HTTP 404 pelo {@link GlobalExceptionHandler}.
 *
 * <p>{@code @StandardException} gera os construtores usuais
 * (vazio, mensagem, causa, mensagem + causa).</p>
 */
@StandardException
public class ResourceNotFoundException extends RuntimeException {
}
