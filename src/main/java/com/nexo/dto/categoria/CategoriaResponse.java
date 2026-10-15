package com.nexo.dto.categoria;

import com.nexo.entity.Categoria;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Dados de saída de uma categoria. Nunca expõe a entity nem o tenant. */
public record CategoriaResponse(
        UUID id,
        String nome,
        String descricao,
        boolean ativo,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm) {

    public static CategoriaResponse de(Categoria categoria) {
        return new CategoriaResponse(
                categoria.getId(),
                categoria.getNome(),
                categoria.getDescricao(),
                categoria.isAtivo(),
                categoria.getCriadoEm(),
                categoria.getAtualizadoEm());
    }
}
