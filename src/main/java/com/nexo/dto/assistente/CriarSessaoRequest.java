package com.nexo.dto.assistente;

import com.nexo.entity.ContextoChat;

/** Corpo opcional de {@code POST /api/assistente/sessoes}. Sem contexto, a sessão é de {@code CONHECIMENTO}. */
public record CriarSessaoRequest(ContextoChat contexto) {
}
