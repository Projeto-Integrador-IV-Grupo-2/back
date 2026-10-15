package com.nexo.service.video;

/**
 * Dados de um vídeo do Vimeo obtidos pelo oEmbed.
 *
 * @param duracaoSegundos duração; nula quando o Vimeo não informa (vídeo privado incorporável)
 * @param miniaturaUrl    miniatura; nula quando o Vimeo não informa
 * @param titulo          título no Vimeo; nulo quando não informado
 */
public record VideoVimeo(Integer duracaoSegundos, String miniaturaUrl, String titulo) {
}
