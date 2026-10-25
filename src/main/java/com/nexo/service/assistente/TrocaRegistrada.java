package com.nexo.service.assistente;

import com.nexo.entity.ChatMensagem;
import com.nexo.entity.ChatSessao;

/**
 * O que ficou gravado numa troca com o assistente: a sessão já atualizada
 * pelo {@code SessaoDoChatListener} e as duas mensagens, na ordem em que
 * foram trocadas.
 */
public record TrocaRegistrada(ChatSessao sessao, ChatMensagem pergunta, ChatMensagem resposta) {
}
