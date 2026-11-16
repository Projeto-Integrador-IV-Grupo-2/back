package com.nexo.service.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nexo.config.VimeoProperties;
import com.nexo.exception.CampoInvalidoException;
import com.nexo.exception.ServicoExternoIndisponivelException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Cobre a tradução das respostas do oEmbed do Vimeo, sem rede (MockRestServiceServer). */
class VimeoClientTest {

    private static final String OEMBED = "https://vimeo.test/api/oembed.json";

    private MockRestServiceServer servidor;
    private VimeoClient cliente;

    @BeforeEach
    void prepara() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new VimeoClient(builder.build(), new VimeoProperties(OEMBED, Duration.ofSeconds(1), "https://nexo.test"));
    }

    /** O link vai codificado no parâmetro {@code url}, como o Vimeo espera. */
    private static String oembedDe(String videoId) {
        return OEMBED + "?url=https%3A%2F%2Fvimeo.com%2F" + videoId;
    }

    @Test
    void videoPublicoDevolveDuracaoEMiniatura() {
        servidor.expect(requestTo(oembedDe("22439234")))
                .andRespond(withSuccess("""
                        {"type":"video","title":"The Mountain","duration":185,
                         "thumbnail_url":"https://i.vimeocdn.com/video/1.jpg","video_id":22439234}
                        """, MediaType.APPLICATION_JSON));

        VideoVimeo video = cliente.consultar(" https://vimeo.com/22439234 ");

        assertThat(video.duracaoSegundos()).isEqualTo(185);
        assertThat(video.miniaturaUrl()).isEqualTo("https://i.vimeocdn.com/video/1.jpg");
        assertThat(video.titulo()).isEqualTo("The Mountain");
        servidor.verify();
    }

    @Test
    void videoSemDuracaoInformadaEhAceito() {
        servidor.expect(requestTo(oembedDe("253989945")))
                .andRespond(withSuccess("{\"type\":\"video\",\"video_id\":253989945}", MediaType.APPLICATION_JSON));

        assertThat(cliente.consultar("https://vimeo.com/253989945").duracaoSegundos()).isNull();
    }

    @Test
    void videoInexistenteViraErroNoCampo() {
        servidor.expect(requestTo(oembedDe("1"))).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> cliente.consultar("https://vimeo.com/1"))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessageStartingWith("Vídeo não encontrado no Vimeo")
                .extracting("campo").isEqualTo("videoUrl");
    }

    @Test
    void videoPrivadoViraErroNoCampo() {
        servidor.expect(requestTo(oembedDe("2"))).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> cliente.consultar("https://vimeo.com/2"))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessageContaining("privado");
    }

    @Test
    void linkForaDoVimeoNemChegaAConsultar() {
        assertThatThrownBy(() -> cliente.consultar("https://youtube.com/watch?v=abc"))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessageContaining("link do Vimeo");
        assertThatThrownBy(() -> cliente.consultar("   "))
                .isInstanceOf(CampoInvalidoException.class);
        servidor.verify();
    }

    @Test
    void vimeoForaDoArViraServicoIndisponivel() {
        servidor.expect(requestTo(oembedDe("3"))).andRespond(withServerError());

        assertThatThrownBy(() -> cliente.consultar("https://vimeo.com/3"))
                .isInstanceOf(ServicoExternoIndisponivelException.class);
    }

    @Test
    void gravaOLinkCanonicoMesmoQuandoOLinkColadoTemOutroFormato() {
        servidor.expect(requestTo(OEMBED + "?url=https%3A%2F%2Fvimeo.com%2Fchannels%2Fstaffpicks%2F76979871"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header("Referer", "https://nexo.test"))
                .andRespond(withSuccess("""
                        {"type":"video","duration":62,"video_id":76979871,
                         "html":"<iframe src=\\"https://player.vimeo.com/video/76979871?app_id=122963\\"></iframe>"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(cliente.consultar("https://vimeo.com/channels/staffpicks/76979871").link())
                .isEqualTo("https://vimeo.com/76979871");
        servidor.verify();
    }

    @Test
    void videoNaoListadoMantemOHashNoLinkCanonico() {
        servidor.expect(requestTo(OEMBED + "?url=https%3A%2F%2Fplayer.vimeo.com%2Fvideo%2F123%3Fh%3Dabc123def0"))
                .andRespond(withSuccess("""
                        {"type":"video","video_id":123,
                         "html":"<iframe src=\\"https://player.vimeo.com/video/123?h=abc123def0&amp;app_id=1\\"></iframe>"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(cliente.consultar("https://player.vimeo.com/video/123?h=abc123def0").link())
                .isEqualTo("https://vimeo.com/123/abc123def0");
    }

    @Test
    void hashDoLinkColadoValeQuandoOVimeoNaoDevolveOIframe() {
        servidor.expect(requestTo(OEMBED + "?url=https%3A%2F%2Fvimeo.com%2F123%2Fabc123def0"))
                .andRespond(withSuccess("{\"type\":\"video\",\"video_id\":123}", MediaType.APPLICATION_JSON));

        assertThat(cliente.consultar("https://vimeo.com/123/abc123def0").link())
                .isEqualTo("https://vimeo.com/123/abc123def0");
    }

    @Test
    void limiteDeConsultasDoVimeoNaoCulpaOLink() {
        servidor.expect(requestTo(oembedDe("5")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> cliente.consultar("https://vimeo.com/5"))
                .isInstanceOf(ServicoExternoIndisponivelException.class);
    }
}
