package com.nexo.dto.pdi;

import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.entity.PdiAcao;
import com.nexo.entity.StatusAcaoPdi;
import com.nexo.entity.TipoAcaoPdi;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ação de um item do PDI. Para ação {@code TRILHA}, {@code trilha} e
 * {@code progressoTrilha} (status e percentual do colaborador na trilha) vêm
 * preenchidos; para as demais, ficam nulos.
 */
public record PdiAcaoResponse(
        UUID id,
        TipoAcaoPdi tipo,
        String descricao,
        ProgressoTrilhaResponse.Recurso trilha,
        LocalDate prazo,
        StatusAcaoPdi status,
        OffsetDateTime concluidoEm,
        ProgressoTrilhaResponse progressoTrilha) {

    public static PdiAcaoResponse de(PdiAcao acao, ProgressoTrilhaResponse progressoTrilha) {
        ProgressoTrilhaResponse.Recurso trilha = acao.getTrilha() == null
                ? null
                : new ProgressoTrilhaResponse.Recurso(acao.getTrilha().getId(), acao.getTrilha().getNome());
        return new PdiAcaoResponse(
                acao.getId(),
                acao.getTipo(),
                acao.getDescricao(),
                trilha,
                acao.getPrazo(),
                acao.getStatus(),
                acao.getConcluidoEm(),
                progressoTrilha);
    }
}
