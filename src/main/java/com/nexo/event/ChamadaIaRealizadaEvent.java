package com.nexo.event;

import java.time.Duration;

/**
 * Uma chamada ao provedor de IA terminou, com sucesso ou não (issue #53).
 *
 * <p>Publicado pelo {@code ClienteIaComRegistroDeUso}, que envolve o cliente
 * real. Quem registra uso e custo (o {@link UsoDaIaListener}) escuta este
 * evento, e nem o cliente nem quem chama a IA precisam saber disso.</p>
 *
 * <p>Não carrega o prompt nem a resposta: só o que é seguro guardar.</p>
 *
 * @param provedor      provedor configurado ({@code gemini}, {@code fake})
 * @param modelo        modelo usado na chamada
 * @param operacao      geração de texto ou de embeddings
 * @param tokensEntrada tokens do prompt (0 quando o provedor não informa ou em embeddings)
 * @param tokensSaida   tokens da resposta (0 quando o provedor não informa ou em embeddings)
 * @param itens         textos enviados para embedding (1 na geração)
 * @param duracao       tempo total da chamada, com as novas tentativas
 * @param sucesso       falso quando a chamada terminou em erro
 */
public record ChamadaIaRealizadaEvent(String provedor, String modelo, Operacao operacao,
                                      int tokensEntrada, int tokensSaida, int itens,
                                      Duration duracao, boolean sucesso) {

    public enum Operacao {
        GERACAO,
        EMBEDDING
    }
}
