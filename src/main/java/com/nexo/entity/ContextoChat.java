package com.nexo.entity;

/** Assunto de uma conversa com o assistente (enum PostgreSQL {@code contexto_chat}). */
public enum ContextoChat {
    /** Perguntas sobre os documentos da empresa (issue #60). */
    CONHECIMENTO,
    /** Sugestão de trilhas (issue #63). */
    TRILHAS
}
