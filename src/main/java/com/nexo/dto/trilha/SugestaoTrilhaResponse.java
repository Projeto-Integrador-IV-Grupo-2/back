package com.nexo.dto.trilha;

import com.nexo.service.recomendacao.TrilhaCandidata;
import java.util.UUID;

/**
 * Uma trilha indicada ao usuário em {@code GET /api/trilhas/sugestoes}.
 *
 * <p>Os dados vêm sempre da trilha lida do banco — nunca do texto do modelo,
 * que só escolhe o id e escreve o {@code motivo}.</p>
 */
public record SugestaoTrilhaResponse(
        UUID id,
        String nome,
        TrilhaResumoResponse.Recurso area,
        String objetivos,
        String motivo) {

    public static SugestaoTrilhaResponse de(TrilhaCandidata trilha, String motivo) {
        return new SugestaoTrilhaResponse(
                trilha.id(),
                trilha.nome(),
                trilha.areaId() == null
                        ? null
                        : new TrilhaResumoResponse.Recurso(trilha.areaId(), trilha.areaNome()),
                trilha.objetivos(),
                motivo);
    }
}
