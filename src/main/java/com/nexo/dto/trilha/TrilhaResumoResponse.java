package com.nexo.dto.trilha;

import com.nexo.entity.Area;
import com.nexo.entity.Trilha;
import java.util.UUID;

/**
 * Resumo de uma trilha para a listagem (Card 05).
 *
 * <p>Enxuto de propósito: a listagem não carrega descrição, objetivos nem
 * progresso — isso é papel do {@link TrilhaDetalheResponse}. Nunca expõe
 * {@link Trilha} nem {@link Area} diretamente.</p>
 */
public record TrilhaResumoResponse(UUID id, String nome, Recurso area) {

    /** Resumo da área — nunca a entidade completa. */
    public record Recurso(UUID id, String nome) {
    }

    public static TrilhaResumoResponse de(Trilha trilha) {
        return new TrilhaResumoResponse(
                trilha.getId(),
                trilha.getNome(),
                recursoDe(trilha.getArea()));
    }

    private static Recurso recursoDe(Area area) {
        return area == null ? null : new Recurso(area.getId(), area.getNome());
    }
}
