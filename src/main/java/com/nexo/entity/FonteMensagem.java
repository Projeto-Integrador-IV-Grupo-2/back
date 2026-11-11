package com.nexo.entity;

import java.util.UUID;

/**
 * Trecho de documento usado numa resposta do assistente. Gravado como JSONB em
 * {@code chat_mensagem.fontes}, preservando um snapshot da fonte no momento da resposta.
 *
 * @param conteudoId documento de origem
 * @param titulo título do documento no momento da resposta
 * @param chunkId identificador do trecho indexado
 * @param ordem posição do trecho no documento
 * @param trecho texto do trecho que fundamentou a resposta
 */
public record FonteMensagem(UUID conteudoId, String titulo, UUID chunkId, int ordem, String trecho) {

    /** Compatibilidade com fontes antigas que ainda não armazenavam o texto do trecho. */
    public FonteMensagem(UUID conteudoId, String titulo, UUID chunkId, int ordem) {
        this(conteudoId, titulo, chunkId, ordem, null);
    }
}
