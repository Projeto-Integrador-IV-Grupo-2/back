package com.nexo.repository;

import com.nexo.entity.StatusConteudo;
import com.nexo.entity.TipoConteudo;
import java.util.UUID;

/**
 * Filtros de {@code GET /api/conteudos}, todos opcionais e combinados com E.
 *
 * @param titulo      trecho do título (sem diferenciar maiúsculas)
 * @param categoriaId categoria do conteúdo
 * @param areaId      área do conteúdo
 * @param tipo        tipo do conteúdo
 * @param status      situação editorial; para colaborador é sempre forçada a PUBLICADO
 */
public record ConteudoFiltro(
        String titulo,
        UUID categoriaId,
        UUID areaId,
        TipoConteudo tipo,
        StatusConteudo status) {

    public static ConteudoFiltro vazio() {
        return new ConteudoFiltro(null, null, null, null, null);
    }

    public ConteudoFiltro comStatus(StatusConteudo novoStatus) {
        return new ConteudoFiltro(titulo, categoriaId, areaId, tipo, novoStatus);
    }
}
