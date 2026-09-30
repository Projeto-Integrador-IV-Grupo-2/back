package com.nexo.dto.trilha;

import com.nexo.entity.Area;
import com.nexo.entity.Trilha;
import java.util.UUID;

/**
 * Resumo de uma trilha para a listagem (Card 05).
 *
 * <p>Não carrega descrição nem prazo — isso é papel do
 * {@link TrilhaDetalheResponse}. Traz {@code objetivos} e o progresso do
 * usuário autenticado, que o card da listagem exibe no próprio cartão. Nunca
 * expõe {@link Trilha} nem {@link Area} diretamente.</p>
 */
public record TrilhaResumoResponse(
        UUID id,
        String nome,
        Recurso area,
        String objetivos,
        ProgressoTrilhaResponse progresso) {

    /** Resumo da área — nunca a entidade completa. */
    public record Recurso(UUID id, String nome) {
    }

    public static TrilhaResumoResponse de(Trilha trilha, ProgressoTrilhaResponse progresso) {
        return new TrilhaResumoResponse(
                trilha.getId(),
                trilha.getNome(),
                recursoDe(trilha.getArea()),
                trilha.getObjetivos(),
                progresso);
    }

    /** Sem progresso conhecido: o usuário nunca iniciou a trilha. */
    public static TrilhaResumoResponse de(Trilha trilha) {
        return de(trilha, ProgressoTrilhaResponse.naoIniciado(trilha));
    }

    private static Recurso recursoDe(Area area) {
        return area == null ? null : new Recurso(area.getId(), area.getNome());
    }
}
