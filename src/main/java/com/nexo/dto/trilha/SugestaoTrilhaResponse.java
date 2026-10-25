package com.nexo.dto.trilha;

import com.nexo.entity.Area;
import com.nexo.entity.Trilha;
import java.util.UUID;

/**
 * Uma trilha indicada ao usuário em {@code GET /api/trilhas/sugestoes}.
 *
 * <p>Os dados vêm sempre da trilha gravada no banco — nunca do texto do
 * modelo, que só escolhe o id e escreve o {@code motivo}.</p>
 */
public record SugestaoTrilhaResponse(
        UUID id,
        String nome,
        TrilhaResumoResponse.Recurso area,
        String objetivos,
        String motivo) {

    public static SugestaoTrilhaResponse de(Trilha trilha, String motivo) {
        Area area = trilha.getArea();
        return new SugestaoTrilhaResponse(
                trilha.getId(),
                trilha.getNome(),
                area == null ? null : new TrilhaResumoResponse.Recurso(area.getId(), area.getNome()),
                trilha.getObjetivos(),
                motivo);
    }
}
