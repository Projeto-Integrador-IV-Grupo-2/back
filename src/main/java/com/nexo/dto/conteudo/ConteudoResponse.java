package com.nexo.dto.conteudo;

import com.nexo.entity.Area;
import com.nexo.entity.Categoria;
import com.nexo.entity.Conteudo;
import com.nexo.entity.StatusConteudo;
import com.nexo.entity.TipoConteudo;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Dados de saída de um conteúdo. Nunca expõe as entities. */
public record ConteudoResponse(
        UUID id,
        String titulo,
        String descricao,
        TipoConteudo tipo,
        StatusConteudo status,
        Recurso categoria,
        Recurso area,
        String videoUrl,
        Integer duracaoSegundos,
        String miniaturaUrl,
        String corpo,
        String linkUrl,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        OffsetDateTime publicadoEm) {

    /** Resumo de categoria ou área — nunca a entidade completa. */
    public record Recurso(UUID id, String nome) {
    }

    public static ConteudoResponse de(Conteudo conteudo) {
        return new ConteudoResponse(
                conteudo.getId(),
                conteudo.getTitulo(),
                conteudo.getDescricao(),
                conteudo.getTipo(),
                conteudo.getStatus(),
                recursoDe(conteudo.getCategoria()),
                recursoDe(conteudo.getArea()),
                conteudo.getVideoUrl(),
                conteudo.getDuracaoSegundos(),
                conteudo.getMiniaturaUrl(),
                conteudo.getCorpo(),
                conteudo.getLinkUrl(),
                conteudo.getCriadoEm(),
                conteudo.getAtualizadoEm(),
                conteudo.getPublicadoEm());
    }

    private static Recurso recursoDe(Categoria categoria) {
        return categoria == null ? null : new Recurso(categoria.getId(), categoria.getNome());
    }

    private static Recurso recursoDe(Area area) {
        return area == null ? null : new Recurso(area.getId(), area.getNome());
    }
}
