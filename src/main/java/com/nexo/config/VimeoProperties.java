package com.nexo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Integração com o Vimeo lida de {@code app.vimeo.*}.
 *
 * <p>A API usa só o endpoint público de oEmbed, que não exige chave: ele
 * confirma que o vídeo existe e pode ser incorporado e devolve duração e
 * miniatura.</p>
 *
 * @param oembedUrl endereço do oEmbed do Vimeo
 * @param timeout   tempo máximo de espera pela resposta
 * @param referer   endereço do front, enviado como {@code Referer}: vídeo com
 *                  incorporação restrita a domínios só é reconhecido pelo oEmbed
 *                  quando o pedido vem de um domínio liberado
 */
@ConfigurationProperties(prefix = "app.vimeo")
public record VimeoProperties(String oembedUrl, Duration timeout, String referer) {

    public VimeoProperties {
        if (oembedUrl == null || oembedUrl.isBlank()) {
            oembedUrl = "https://vimeo.com/api/oembed.json";
        }
        referer = referer == null || referer.isBlank() ? null : referer.trim();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofSeconds(5);
        }
    }
}
