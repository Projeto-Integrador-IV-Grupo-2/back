package com.nexo.exception;

/** Lançada quando uma operação, embora bem formada, é proibida pelo estado ou pela regra atual. */
public class OperacaoNaoPermitidaException extends NegocioException {

    public OperacaoNaoPermitidaException(String message) {
        super(message);
    }
}
