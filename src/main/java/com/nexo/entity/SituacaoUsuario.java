package com.nexo.entity;

public enum SituacaoUsuario {
    ATIVO,
    INATIVO;

    public boolean isAtivo() {
        return this == ATIVO;
    }

    public static SituacaoUsuario de(boolean ativo) {
        return ativo ? ATIVO : INATIVO;
    }
}
