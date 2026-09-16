package com.nexo.exception;

/**
 * Superclasse das exceções de regra de negócio: uma operação sintaticamente
 * válida que é recusada por violar uma regra do domínio (não um recurso
 * inexistente, nem um payload malformado).
 *
 * <p>Mapeada para HTTP 409 (Conflict) pelo {@link GlobalExceptionHandler}.</p>
 */
public class NegocioException extends RuntimeException {

    public NegocioException(String message) {
        super(message);
    }
}
