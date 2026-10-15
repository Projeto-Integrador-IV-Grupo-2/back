package com.nexo.dto.etapa;

import com.nexo.entity.Conteudo;
import com.nexo.entity.StatusConteudo;
import com.nexo.entity.TipoConteudo;
import com.nexo.entity.TrilhaEtapa;
import java.util.UUID;

/**
 * Uma etapa da trilha com o conteúdo resumido e se o usuário autenticado já a
 * concluiu. Nunca expõe as entities.
 */
public record EtapaResponse(
        UUID id,
        int ordem,
        String titulo,
        String descricao,
        ConteudoResumo conteudo,
        boolean concluida) {

    /** O necessário para a tela de estudo tocar o vídeo ou mostrar o material. */
    public record ConteudoResumo(
            UUID id,
            String titulo,
            TipoConteudo tipo,
            StatusConteudo status,
            String videoUrl,
            Integer duracaoSegundos,
            String miniaturaUrl,
            String corpo,
            String linkUrl) {

        static ConteudoResumo de(Conteudo conteudo) {
            return new ConteudoResumo(
                    conteudo.getId(),
                    conteudo.getTitulo(),
                    conteudo.getTipo(),
                    conteudo.getStatus(),
                    conteudo.getVideoUrl(),
                    conteudo.getDuracaoSegundos(),
                    conteudo.getMiniaturaUrl(),
                    conteudo.getCorpo(),
                    conteudo.getLinkUrl());
        }
    }

    public static EtapaResponse de(TrilhaEtapa etapa, boolean concluida) {
        return new EtapaResponse(
                etapa.getId(),
                etapa.getOrdem(),
                etapa.getTitulo(),
                etapa.getDescricao(),
                ConteudoResumo.de(etapa.getConteudo()),
                concluida);
    }
}
