package com.nexo.exception;

/** Lançada ao tentar cadastrar (ou uma segunda vez, em corrida) um usuário com e-mail já existente. */
public class EmailDuplicadoException extends NegocioException {

    public EmailDuplicadoException(String email) {
        super("Já existe um usuário cadastrado com o e-mail: " + email);
    }
}
