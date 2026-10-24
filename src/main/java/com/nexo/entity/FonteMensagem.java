package com.nexo.entity;

import java.util.UUID;

/**
 * Trecho de documento usado numa resposta do assistente. Gravado como JSON em
 * {@code chat_mensagem.fontes}.
 *
 * @param conteudoId documento de origem
 * @param titulo     título do documento no momento da resposta
 * @param chunkId    trecho usado
 * @param ordem      posição do trecho no documento
 */
public record FonteMensagem(UUID conteudoId, String titulo, UUID chunkId, int ordem) {
}
