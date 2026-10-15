package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.conteudo.ConteudoRequest;
import com.nexo.entity.Conteudo;
import com.nexo.entity.TipoConteudo;
import com.nexo.exception.CampoInvalidoException;
import com.nexo.service.video.VideoVimeo;
import com.nexo.service.video.VimeoClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConteudoFactoryTest {

    private static final String VIDEO = "https://vimeo.com/22439234";

    @Mock
    private VimeoClient vimeoClient;

    @InjectMocks
    private ConteudoFactory factory;

    @Test
    void videoBuscaDuracaoEMiniaturaNoVimeo() {
        when(vimeoClient.consultar(VIDEO)).thenReturn(new VideoVimeo(185, "thumb.jpg", "The Mountain"));

        Conteudo conteudo = factory.criar(pedido(TipoConteudo.VIDEO, VIDEO, null, null));

        assertThat(conteudo.getTipo()).isEqualTo(TipoConteudo.VIDEO);
        assertThat(conteudo.getVideoUrl()).isEqualTo(VIDEO);
        assertThat(conteudo.getDuracaoSegundos()).isEqualTo(185);
        assertThat(conteudo.getMiniaturaUrl()).isEqualTo("thumb.jpg");
    }

    @Test
    void editarSemTrocarOVideoNaoConsultaOVimeoDeNovo() {
        when(vimeoClient.consultar(VIDEO)).thenReturn(new VideoVimeo(185, "thumb.jpg", null));
        Conteudo conteudo = factory.criar(pedido(TipoConteudo.VIDEO, VIDEO, null, null));

        factory.aplicarMaterial(conteudo, pedido(TipoConteudo.VIDEO, VIDEO, null, null));

        verify(vimeoClient, times(1)).consultar(anyString());
    }

    @Test
    void textoExigeCorpo() {
        assertThatThrownBy(() -> factory.criar(pedido(TipoConteudo.TEXTO, null, "  ", null)))
                .isInstanceOf(CampoInvalidoException.class)
                .extracting("campo").isEqualTo("corpo");
    }

    @Test
    void linkExigeUrlCompleta() {
        assertThatThrownBy(() -> factory.criar(pedido(TipoConteudo.LINK, null, null, "site.com")))
                .isInstanceOf(CampoInvalidoException.class)
                .extracting("campo").isEqualTo("linkUrl");
    }

    @Test
    void trocarDeTipoLimpaOMaterialAntigo() {
        when(vimeoClient.consultar(VIDEO)).thenReturn(new VideoVimeo(185, "thumb.jpg", null));
        Conteudo conteudo = factory.criar(pedido(TipoConteudo.VIDEO, VIDEO, null, null));

        factory.aplicarMaterial(conteudo, pedido(TipoConteudo.TEXTO, null, "Leia isto", null));

        assertThat(conteudo.getTipo()).isEqualTo(TipoConteudo.TEXTO);
        assertThat(conteudo.getCorpo()).isEqualTo("Leia isto");
        assertThat(conteudo.getVideoUrl()).isNull();
        assertThat(conteudo.getDuracaoSegundos()).isNull();
    }

    @Test
    void textoNaoConsultaOVimeo() {
        factory.criar(pedido(TipoConteudo.TEXTO, VIDEO, "corpo", null));

        verify(vimeoClient, never()).consultar(anyString());
    }

    private static ConteudoRequest pedido(TipoConteudo tipo, String videoUrl, String corpo, String linkUrl) {
        return new ConteudoRequest("Título", null, tipo, null, null, videoUrl, corpo, linkUrl);
    }
}
