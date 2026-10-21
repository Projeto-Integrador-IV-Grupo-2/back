package com.nexo.service.indexacao;

import java.util.UUID;

/**
 * Trecho devolvido pela busca do assistente.
 *
 * @param distancia distância de cosseno até a pergunta (menor = mais parecido)
 */
public record TrechoEncontrado(UUID chunkId, UUID conteudoId, String titulo, int ordem, String texto,
                               double distancia) {
}
