package com.nexo.exception;

/**
 * Parâmetro de requisição com valor que a API não suporta (ex.: formato de
 * exportação desconhecido). Mapeada para HTTP 400 pelo {@link GlobalExceptionHandler}.
 */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String message) {
        super(message);
    }
}
