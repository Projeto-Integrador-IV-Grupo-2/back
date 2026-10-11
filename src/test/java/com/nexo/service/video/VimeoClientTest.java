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
        cliente = new VimeoClient(builder.build(), new VimeoProperties(OEMBED, Duration.ofSeconds(1)));
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
                .hasMessage("Vídeo não encontrado no Vimeo")
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
}
