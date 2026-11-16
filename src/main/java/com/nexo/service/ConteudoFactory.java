package com.nexo.service;

import com.nexo.dto.conteudo.ConteudoRequest;
import com.nexo.entity.Conteudo;
import com.nexo.entity.StatusConteudo;
import com.nexo.entity.TipoConteudo;
import com.nexo.exception.CampoInvalidoException;
import com.nexo.service.video.VideoVimeo;
import com.nexo.service.video.VimeoClient;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Monta o material de um conteúdo conforme o tipo (Factory Method).
 *
 * <p>Cada tipo tem seu próprio método de montagem — {@link #video},
 * {@link #texto}, {@link #link} e {@link #documento} —, e todos limpam os campos dos outros tipos,
 * para um conteúdo que trocou de tipo não carregar material velho. É por aqui
 * que a API cria um {@link Conteudo}, já em rascunho; o service só completa
 * tenant, autor, título, categoria e área. ({@link Conteudo#novoDocumento} é
 * o atalho da entidade para montar um documento fora da API, como nos
 * testes.)</p>
 */
@Component
public class ConteudoFactory {

    private final VimeoClient vimeoClient;

    public ConteudoFactory(VimeoClient vimeoClient) {
        this.vimeoClient = vimeoClient;
    }

    /** Conteúdo novo, em rascunho, com o material do tipo pedido. */
    public Conteudo criar(ConteudoRequest request) {
        Conteudo conteudo = new Conteudo();
        conteudo.setStatus(StatusConteudo.RASCUNHO);
        aplicarMaterial(conteudo, request);
        return conteudo;
    }

    /**
     * Aplica o material do pedido num conteúdo existente. O Vimeo só é
     * consultado de novo quando o link do vídeo muda.
     */
    public void aplicarMaterial(Conteudo conteudo, ConteudoRequest request) {
        TipoConteudo tipo = request.tipo();
        switch (tipo) {
            case VIDEO -> video(conteudo, request.videoUrl());
            case TEXTO -> texto(conteudo, request.corpo());
            case LINK -> link(conteudo, request.linkUrl());
            case DOCUMENTO -> documento(conteudo);
        }
        conteudo.setTipo(tipo);
    }

    private void video(Conteudo conteudo, String videoUrl) {
        String link = videoUrl == null ? null : videoUrl.trim();
        boolean mesmoVideo = conteudo.getTipo() == TipoConteudo.VIDEO
                && Objects.equals(conteudo.getVideoUrl(), link);
        if (!mesmoVideo) {
            VideoVimeo video = vimeoClient.consultar(link);
            // Grava o formato que o player entende, não o que foi colado.
            conteudo.setVideoUrl(video.link() == null ? link : video.link());
            conteudo.setDuracaoSegundos(video.duracaoSegundos());
            conteudo.setMiniaturaUrl(video.miniaturaUrl());
        }
        conteudo.setCorpo(null);
        conteudo.setLinkUrl(null);
    }

    private void texto(Conteudo conteudo, String corpo) {
        if (corpo == null || corpo.isBlank()) {
            throw new CampoInvalidoException("corpo", "Texto do conteúdo é obrigatório");
        }
        conteudo.setCorpo(corpo.trim());
        limparVideo(conteudo);
        conteudo.setLinkUrl(null);
    }

    private void link(Conteudo conteudo, String linkUrl) {
        conteudo.setLinkUrl(exigirUrlHttp(linkUrl));
        limparVideo(conteudo);
        conteudo.setCorpo(null);
    }

    /** O material do documento são os arquivos anexados: nada de vídeo, corpo ou link. */
    private static void documento(Conteudo conteudo) {
        limparVideo(conteudo);
        conteudo.setCorpo(null);
        conteudo.setLinkUrl(null);
    }

    private static void limparVideo(Conteudo conteudo) {
        conteudo.setVideoUrl(null);
        conteudo.setDuracaoSegundos(null);
        conteudo.setMiniaturaUrl(null);
    }

    private static String exigirUrlHttp(String linkUrl) {
        if (linkUrl == null || linkUrl.isBlank()) {
            throw new CampoInvalidoException("linkUrl", "Link é obrigatório");
        }
        String link = linkUrl.trim();
        try {
            URI uri = new URI(link);
            boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!http || uri.getHost() == null) {
                throw new CampoInvalidoException("linkUrl", "Informe um link completo (https://...)");
            }
        } catch (URISyntaxException ex) {
            throw new CampoInvalidoException("linkUrl", "Informe um link completo (https://...)");
        }
        return link;
    }
}
