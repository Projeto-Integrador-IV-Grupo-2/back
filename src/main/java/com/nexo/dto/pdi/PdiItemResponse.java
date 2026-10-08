package com.nexo.dto.pdi;

import com.nexo.entity.Competencia;
import com.nexo.entity.PdiItem;
import com.nexo.entity.TipoCompetencia;
import java.util.List;
import java.util.UUID;

/** Competência do PDI com seus níveis e as ações ligadas a ela. */
public record PdiItemResponse(
        UUID id,
        CompetenciaResumo competencia,
        int nivelAtual,
        int nivelAlvo,
        String observacao,
        List<PdiAcaoResponse> acoes) {

    public record CompetenciaResumo(UUID id, String nome, TipoCompetencia tipo) {

        static CompetenciaResumo de(Competencia competencia) {
            return new CompetenciaResumo(competencia.getId(), competencia.getNome(), competencia.getTipo());
        }
    }

    public static PdiItemResponse de(PdiItem item, List<PdiAcaoResponse> acoes) {
        return new PdiItemResponse(
                item.getId(),
                CompetenciaResumo.de(item.getCompetencia()),
                item.getNivelAtual(),
                item.getNivelAlvo(),
                item.getObservacao(),
                acoes);
    }
}
