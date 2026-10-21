package com.nexo.service.assistente;

import com.nexo.entity.ChatMensagem;
import com.nexo.entity.ContextoChat;
import com.nexo.service.indexacao.TrechoEncontrado;
import java.util.List;

/**
 * Monta o prompt de um contexto de conversa. Implementações são singletons
 * sem estado; quem escolhe a certa é a {@link MontadorDePromptFactory}.
 */
public interface MontadorDePrompt {

    ContextoChat contexto();

    /**
     * @param pergunta  pergunta atual do usuário
     * @param trechos   trechos relevantes, todos do tenant e publicados
     * @param historico mensagens anteriores da sessão, em ordem cronológica
     */
    PromptMontado montar(String pergunta, List<TrechoEncontrado> trechos, List<ChatMensagem> historico);
}
