package com.nexo.exception;

/**
 * Pedidos de "esqueci minha senha" demais para o mesmo e-mail ou IP (issue
 * #76). Mapeada para HTTP 429, com o header {@code Retry-After}.
 */
public class LimiteDePedidosExcedidoException extends RuntimeException {

    private final long segundosParaTentarDeNovo;

    public LimiteDePedidosExcedidoException(String message, long segundosParaTentarDeNovo) {
        super(message);
        this.segundosParaTentarDeNovo = segundosParaTentarDeNovo;
    }

    public long getSegundosParaTentarDeNovo() {
        return segundosParaTentarDeNovo;
    }
}
