package com.nexo.exception;

/**
 * Link de redefinição de senha inventado, expirado, já usado ou de usuário
 * inativo (issue #76). Mapeada para HTTP 400 pelo {@link GlobalExceptionHandler},
 * com a mesma mensagem para todos os casos — a resposta não diz qual foi.
 */
public class TokenRedefinicaoInvalidoException extends RuntimeException {

    public TokenRedefinicaoInvalidoException() {
        super("Este link de redefinição é inválido ou expirou. Peça um novo link.");
    }
}
