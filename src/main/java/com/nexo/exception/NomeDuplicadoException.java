package com.nexo.exception;

/** Lançada ao tentar gravar um recurso de catálogo com nome já usado no mesmo tenant. */
public class NomeDuplicadoException extends NegocioException {

    public NomeDuplicadoException(String recurso, String nome) {
        super("Já existe " + recurso + " com o nome: " + nome);
    }
}
