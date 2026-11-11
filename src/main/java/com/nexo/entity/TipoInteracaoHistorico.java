package com.nexo.entity;

/** Tipos de interação armazenados no histórico de auditoria. */
public enum TipoInteracaoHistorico {
    TRILHA_ACESSADA,
    TRILHA_INICIADA,
    TRILHA_CONCLUIDA,
    /** O usuário perguntou algo ao assistente (issue #59). */
    ASSISTENTE_CONSULTADO,
    /** O usuário redefiniu a senha pelo link de "esqueci minha senha" (issue #76). */
    SENHA_REDEFINIDA
}
