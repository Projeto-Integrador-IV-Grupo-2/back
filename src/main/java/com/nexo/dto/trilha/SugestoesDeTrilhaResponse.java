package com.nexo.dto.trilha;

import java.util.List;

/**
 * Resposta de {@code GET /api/trilhas/sugestoes}.
 *
 * @param geradoPorIa falso quando o modelo não respondeu e a lista veio da
 *                    recomendação por área
 * @param aviso       texto a mostrar ao usuário quando {@code geradoPorIa} é
 *                    falso; {@code null} no caminho normal
 * @param sugestoes   até cinco trilhas, cada uma com o motivo da indicação
 */
public record SugestoesDeTrilhaResponse(boolean geradoPorIa, String aviso, List<SugestaoTrilhaResponse> sugestoes) {

    public static final String AVISO_SEM_IA =
            "Não foi possível gerar as sugestões com a IA agora. Estas são as trilhas da sua área.";

    public static SugestoesDeTrilhaResponse daIa(List<SugestaoTrilhaResponse> sugestoes) {
        return new SugestoesDeTrilhaResponse(true, null, sugestoes);
    }

    public static SugestoesDeTrilhaResponse daArea(List<SugestaoTrilhaResponse> sugestoes) {
        return new SugestoesDeTrilhaResponse(false, AVISO_SEM_IA, sugestoes);
    }
}
