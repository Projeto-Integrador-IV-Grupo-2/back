package com.nexo.dto.pdi;

import com.nexo.entity.Pdi;
import com.nexo.entity.StatusPdi;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Linha da listagem de PDIs ("PDIs da equipe"). */
public record PdiResumoResponse(
        UUID id,
        PessoaResumo colaborador,
        PessoaResumo gestor,
        String ciclo,
        LocalDate inicio,
        LocalDate fim,
        StatusPdi status,
        int percentual,
        long totalAcoes,
        long acoesConcluidas,
        OffsetDateTime criadoEm) {

    /** A partir do detalhe: mesmo percentual (dinâmico) da tela do PDI. */
    public static PdiResumoResponse de(Pdi pdi, PdiDetalheResponse detalhe) {
        return new PdiResumoResponse(
                pdi.getId(),
                PessoaResumo.de(pdi.getColaborador()),
                PessoaResumo.de(pdi.getGestor()),
                pdi.getCiclo(),
                pdi.getInicio(),
                pdi.getFim(),
                pdi.getStatus(),
                detalhe.percentual(),
                detalhe.totalAcoes(),
                detalhe.acoesConcluidas(),
                pdi.getCriadoEm());
    }

    public static PdiResumoResponse de(Pdi pdi, long totalAcoes, long acoesConcluidas) {
        return new PdiResumoResponse(
                pdi.getId(),
                PessoaResumo.de(pdi.getColaborador()),
                PessoaResumo.de(pdi.getGestor()),
                pdi.getCiclo(),
                pdi.getInicio(),
                pdi.getFim(),
                pdi.getStatus(),
                PercentualPdi.de(acoesConcluidas, totalAcoes),
                totalAcoes,
                acoesConcluidas,
                pdi.getCriadoEm());
    }
}
