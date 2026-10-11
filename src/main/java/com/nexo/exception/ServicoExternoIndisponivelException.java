package com.nexo.exception;

/**
 * Um serviço externo (ex.: Vimeo) não respondeu ou respondeu com erro dele.
 * Mapeada para HTTP 502 pelo {@link GlobalExceptionHandler}: a falha não é do
 * cliente, e repetir mais tarde pode funcionar.
 */
public class ServicoExternoIndisponivelException extends RuntimeException {

    public ServicoExternoIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
