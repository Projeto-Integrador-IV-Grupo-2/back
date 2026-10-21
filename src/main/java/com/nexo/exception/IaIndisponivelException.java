package com.nexo.exception;

/**
 * O assistente de IA não pode responder agora: chave não configurada, timeout,
 * limite de uso do provedor ou erro dele.
 *
 * <p>Mapeada para HTTP 503 pelo {@link GlobalExceptionHandler}. A mensagem é
 * mostrada ao usuário, então nunca carrega o prompt, a pergunta nem a chave.</p>
 */
public class IaIndisponivelException extends RuntimeException {

    public IaIndisponivelException(String message) {
        super(message);
    }

    public IaIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
