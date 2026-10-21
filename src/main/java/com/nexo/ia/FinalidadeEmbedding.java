package com.nexo.ia;

/** Lado da busca em que o embedding vai ser usado. */
public enum FinalidadeEmbedding {
    /** Trecho de documento guardado em {@code conteudo_chunk}. */
    DOCUMENTO,
    /** Pergunta do usuário comparada com os trechos. */
    CONSULTA
}
