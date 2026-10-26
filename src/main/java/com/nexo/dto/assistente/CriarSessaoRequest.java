package com.nexo.dto.assistente;

import com.nexo.entity.ChatSessao;
import com.nexo.entity.ContextoChat;
import jakarta.validation.constraints.Size;

/**
 * Corpo opcional de {@code POST /api/assistente/sessoes}.
 *
 * @param titulo   título já escolhido por quem abre a conversa (a tela manda o
 *                 começo da primeira pergunta). Sem ele, a sessão nasce sem
 *                 título e ganha um na primeira troca de mensagens.
 * @param contexto sem contexto, a sessão é de {@code CONHECIMENTO}
 */
public record CriarSessaoRequest(
        @Size(max = ChatSessao.TAMANHO_MAXIMO_TITULO,
                message = "O título pode ter no máximo 200 caracteres")
        String titulo,
        ContextoChat contexto) {
}
