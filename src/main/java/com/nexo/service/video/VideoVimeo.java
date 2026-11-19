package com.nexo.service.video;

/**
 * Dados de um vídeo do Vimeo obtidos pelo oEmbed.
 *
 * @param duracaoSegundos duração; nula quando o Vimeo não informa (vídeo privado incorporável)
 * @param miniaturaUrl    miniatura; nula quando o Vimeo não informa
 * @param titulo          título no Vimeo; nulo quando não informado
 * @param link            link no formato {@code https://vimeo.com/{id}[/{hash}]}; nulo
 *                        quando o Vimeo não informou o id do vídeo
 */
public record VideoVimeo(Integer duracaoSegundos, String miniaturaUrl, String titulo, String link) {

    /** Sem link canônico: vale o que foi digitado. */
    public VideoVimeo(Integer duracaoSegundos, String miniaturaUrl, String titulo) {
        this(duracaoSegundos, miniaturaUrl, titulo, null);
    }
}
