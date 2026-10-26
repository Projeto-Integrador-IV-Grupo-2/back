package com.nexo.service.recomendacao;

/**
 * Uma trilha indicada a um usuário e o porquê.
 *
 * @param trilha trilha do catálogo que foi entregue à estratégia — nunca uma
 *               trilha inventada
 * @param motivo frase curta, em português, mostrada ao lado da indicação
 */
public record TrilhaSugerida(TrilhaCandidata trilha, String motivo) {
}
