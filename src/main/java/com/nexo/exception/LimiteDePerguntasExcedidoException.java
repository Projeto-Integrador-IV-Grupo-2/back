package com.nexo.exception;

/**
 * O usuário passou do limite de perguntas por minuto ao assistente.
 * Mapeada para HTTP 429, com o header {@code Retry-After}.
 */
public class LimiteDePerguntasExcedidoException extends RuntimeException {

    private final long segundosParaTentarDeNovo;

    public LimiteDePerguntasExcedidoException(String message, long segundosParaTentarDeNovo) {
        super(message);
        this.segundosParaTentarDeNovo = segundosParaTentarDeNovo;
    }

    public long getSegundosParaTentarDeNovo() {
        return segundosParaTentarDeNovo;
    }
}
