package com.nexo.dto.assistente;

import com.nexo.entity.FonteMensagem;
import java.util.UUID;

/** Fonte detalhada usada para fundamentar uma resposta do assistente. */
public record FonteAssistenteResponse(UUID documentoId, String titulo, UUID trechoId, int ordem, String trecho) {

    public static FonteAssistenteResponse de(FonteMensagem fonte) {
        return new FonteAssistenteResponse(fonte.conteudoId(), fonte.titulo(), fonte.chunkId(), fonte.ordem(),
                fonte.trecho());
    }
}
