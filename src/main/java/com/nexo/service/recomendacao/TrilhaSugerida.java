package com.nexo.service.recomendacao;

import com.nexo.entity.Trilha;

/**
 * Uma trilha indicada a um usuário e o porquê.
 *
 * @param trilha trilha do catálogo que foi enviado à estratégia — nunca uma
 *               trilha inventada
 * @param motivo frase curta, em português, mostrada ao lado da indicação
 */
public record TrilhaSugerida(Trilha trilha, String motivo) {
}
