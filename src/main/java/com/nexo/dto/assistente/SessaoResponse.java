package com.nexo.dto.assistente;

import com.nexo.entity.ChatSessao;
import com.nexo.entity.ContextoChat;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Sessão de conversa com o assistente. {@code titulo} é nulo até a primeira pergunta. */
public record SessaoResponse(UUID id, ContextoChat contexto, String titulo, OffsetDateTime iniciadaEm,
                             OffsetDateTime ultimaInteracao) {

    public static SessaoResponse de(ChatSessao sessao) {
        return new SessaoResponse(sessao.getId(), sessao.getContexto(), sessao.getTitulo(),
                sessao.getIniciadaEm(), sessao.getUltimaInteracao());
    }
}
