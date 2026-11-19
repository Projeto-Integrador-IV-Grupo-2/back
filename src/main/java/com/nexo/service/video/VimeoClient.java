package com.nexo.service.video;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexo.config.VimeoConfig;
import com.nexo.config.VimeoProperties;
import com.nexo.exception.CampoInvalidoException;
import com.nexo.exception.ServicoExternoIndisponivelException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente do oEmbed do Vimeo.
 *
 * <p>Singleton do Spring e sem estado: o {@link RestClient} é montado uma vez,
 * em {@link VimeoConfig}, e reaproveitado por todas as chamadas.</p>
 *
 * <p>O oEmbed responde 404 para vídeo inexistente e 403/401 para vídeo que não
 * pode ser incorporado — os dois casos viram {@link CampoInvalidoException} no
 * campo {@code videoUrl} (HTTP 400). Falha de rede ou erro 5xx do Vimeo vira
 * {@link ServicoExternoIndisponivelException} (HTTP 502).</p>
 */
@Component
public class VimeoClient {

    static final String CAMPO = "videoUrl";

    private static final Set<String> HOSTS_ACEITOS = Set.of("vimeo.com", "www.vimeo.com", "player.vimeo.com");

    private static final Pattern HASH_NO_PARAMETRO = Pattern.compile("[?&;]h=([0-9a-fA-F]+)");
    private static final Pattern HASH_NO_CAMINHO =
            Pattern.compile("vimeo\\.com/(?:video/)?\\d+/([0-9a-fA-F]{6,})(?:[/?#]|$)");

    private final RestClient restClient;
    private final String oembedUrl;
    private final String referer;

    public VimeoClient(@Qualifier(VimeoConfig.REST_CLIENT) RestClient restClient, VimeoProperties properties) {
        this.restClient = restClient;
        this.oembedUrl = properties.oembedUrl();
        this.referer = properties.referer();
    }

    /**
     * Valida o link no Vimeo e devolve duração e miniatura.
     *
     * @throws CampoInvalidoException              link fora do Vimeo, inexistente ou não incorporável
     * @throws ServicoExternoIndisponivelException Vimeo fora do ar
     */
    public VideoVimeo consultar(String videoUrl) {
        String link = exigirLinkDoVimeo(videoUrl);
        try {
            RespostaOembed resposta = restClient.get()
                    .uri(oembedUrl + "?url={url}", link)
                    .headers(headers -> {
                        if (referer != null) {
                            headers.set(HttpHeaders.REFERER, referer);
                        }
                    })
                    .retrieve()
                    .body(RespostaOembed.class);
            if (resposta == null) {
                throw new ServicoExternoIndisponivelException("O Vimeo respondeu sem conteúdo", null);
            }
            return new VideoVimeo(resposta.duracao(), resposta.miniatura(), resposta.titulo(),
                    linkCanonico(link, resposta));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 429) {
                // Limite de consultas do Vimeo: o link pode estar certo, então não é erro do campo.
                throw new ServicoExternoIndisponivelException(
                        "O Vimeo está limitando as consultas agora. Tente novamente em instantes.", ex);
            }
            throw new CampoInvalidoException(CAMPO, mensagemPara(ex.getStatusCode()));
        } catch (RestClientException ex) {
            throw new ServicoExternoIndisponivelException(
                    "Não foi possível consultar o Vimeo agora. Tente novamente em instantes.", ex);
        }
    }

    /**
     * {@code https://vimeo.com/{id}} (ou {@code .../{id}/{hash}} para vídeo não
     * listado), montado com o id que o próprio Vimeo devolveu.
     *
     * <p>O oEmbed aceita vários formatos — {@code /channels/x/123},
     * {@code /groups/x/videos/123}, {@code player.vimeo.com/video/123} —, mas
     * o player do front só precisa de um. Gravar sempre o mesmo formato evita
     * link aceito no cadastro que depois não toca na trilha.</p>
     */
    static String linkCanonico(String link, RespostaOembed resposta) {
        if (resposta.videoId() == null) {
            return link;
        }
        String hash = hashDe(resposta.html());
        if (hash == null) {
            hash = hashDe(link);
        }
        return "https://vimeo.com/" + resposta.videoId() + (hash == null ? "" : "/" + hash);
    }

    /** O {@code h} dos vídeos não listados: {@code ?h=abc} ou {@code vimeo.com/123/abc}. */
    private static String hashDe(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher parametro = HASH_NO_PARAMETRO.matcher(texto);
        if (parametro.find()) {
            return parametro.group(1);
        }
        Matcher caminho = HASH_NO_CAMINHO.matcher(texto);
        return caminho.find() ? caminho.group(1) : null;
    }

    private static String mensagemPara(HttpStatusCode status) {
        if (status.value() == 401 || status.value() == 403) {
            return "Este vídeo do Vimeo é privado ou não permite incorporação neste site. "
                    + "No Vimeo, libere a incorporação (Privacidade > Incorporar) e tente de novo";
        }
        return "Vídeo não encontrado no Vimeo. Confira o link e se o vídeo não é privado";
    }

    private static String exigirLinkDoVimeo(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new CampoInvalidoException(CAMPO, "Link do vídeo é obrigatório");
        }
        String link = videoUrl.trim();
        try {
            URI uri = new URI(link);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean esquemaValido = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
            if (!esquemaValido || !HOSTS_ACEITOS.contains(host)) {
                throw new CampoInvalidoException(CAMPO, "Informe um link do Vimeo (https://vimeo.com/...)");
            }
        } catch (URISyntaxException ex) {
            throw new CampoInvalidoException(CAMPO, "Informe um link do Vimeo (https://vimeo.com/...)");
        }
        return link;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaOembed(
            @JsonProperty("duration") Integer duracao,
            @JsonProperty("thumbnail_url") String miniatura,
            @JsonProperty("title") String titulo,
            @JsonProperty("video_id") Long videoId,
            @JsonProperty("html") String html) {
    }
}
