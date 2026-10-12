package com.nexo.dto.competencia;

import com.nexo.entity.Competencia;
import com.nexo.entity.TipoCompetencia;
import java.util.UUID;

/** Dados de saída de uma competência (nunca a entidade). */
public record CompetenciaResponse(UUID id, String nome, String descricao, TipoCompetencia tipo, boolean ativo) {

    public static CompetenciaResponse de(Competencia competencia) {
        return new CompetenciaResponse(
                competencia.getId(),
                competencia.getNome(),
                competencia.getDescricao(),
                competencia.getTipo(),
                competencia.isAtivo());
    }
}
