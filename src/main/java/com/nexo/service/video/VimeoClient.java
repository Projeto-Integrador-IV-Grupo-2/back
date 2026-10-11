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
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final RestClient restClient;
    private final String oembedUrl;

    public VimeoClient(@Qualifier(VimeoConfig.REST_CLIENT) RestClient restClient, VimeoProperties properties) {
        this.restClient = restClient;
        this.oembedUrl = properties.oembedUrl();
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
                    .retrieve()
                    .body(RespostaOembed.class);
            if (resposta == null) {
                throw new ServicoExternoIndisponivelException("O Vimeo respondeu sem conteúdo", null);
            }
            return new VideoVimeo(resposta.duracao(), resposta.miniatura(), resposta.titulo());
        } catch (HttpClientErrorException ex) {
            throw new CampoInvalidoException(CAMPO, mensagemPara(ex.getStatusCode()));
        } catch (RestClientException ex) {
            throw new ServicoExternoIndisponivelException(
                    "Não foi possível consultar o Vimeo agora. Tente novamente em instantes.", ex);
        }
    }

    private static String mensagemPara(HttpStatusCode status) {
        if (status.value() == 401 || status.value() == 403) {
            return "Este vídeo do Vimeo é privado ou não permite incorporação";
        }
        return "Vídeo não encontrado no Vimeo";
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
            @JsonProperty("title") String titulo) {
    }
}
