package com.nexo.entity;

/** Situação da indexação de um conteúdo para a IA (enum PostgreSQL {@code status_indexacao}). */
public enum StatusIndexacao {
    PENDENTE,
    INDEXADO,
    FALHOU
}
