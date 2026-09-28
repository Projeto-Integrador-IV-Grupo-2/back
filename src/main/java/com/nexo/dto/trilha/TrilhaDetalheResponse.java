package com.nexo.dto.trilha;

import com.nexo.entity.Area;
import com.nexo.entity.Trilha;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Detalhe completo de uma trilha + progresso do usuário autenticado (Card 08).
 * Nunca expõe {@link Trilha} nem {@link Area} diretamente.
 */
public record TrilhaDetalheResponse(
        UUID id,
        String nome,
        String descricao,
        Recurso area,
        String objetivos,
        Integer prazoDias,
        boolean ativo,
        OffsetDateTime criadoEm,
        ProgressoTrilhaResponse progresso) {

    /** Resumo da área — nunca a entidade completa. */
    public record Recurso(UUID id, String nome) {
    }

    public static TrilhaDetalheResponse de(Trilha trilha, ProgressoTrilhaResponse progresso) {
        return new TrilhaDetalheResponse(
                trilha.getId(),
                trilha.getNome(),
                trilha.getDescricao(),
                recursoDe(trilha.getArea()),
                trilha.getObjetivos(),
                trilha.getPrazoDias(),
                trilha.isAtivo(),
                trilha.getCriadoEm(),
                progresso);
    }

    private static Recurso recursoDe(Area area) {
        return area == null ? null : new Recurso(area.getId(), area.getNome());
    }
}
