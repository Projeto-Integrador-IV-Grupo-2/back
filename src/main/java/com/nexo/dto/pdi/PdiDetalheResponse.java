package com.nexo.dto.pdi;

import com.nexo.entity.Pdi;
import com.nexo.entity.StatusAcaoPdi;
import com.nexo.entity.StatusPdi;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PDI completo: competências (itens) e, em cada uma, as ações — com o
 * progresso do colaborador nas trilhas ligadas. Usado no detalhe e em
 * {@code GET /api/pdis/meu}.
 */
public record PdiDetalheResponse(
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
        OffsetDateTime criadoEm,
        List<PdiItemResponse> itens) {

    public static PdiDetalheResponse de(Pdi pdi, List<PdiItemResponse> itens) {
        long total = itens.stream().mapToLong(item -> item.acoes().size()).sum();
        long concluidas = itens.stream()
                .flatMap(item -> item.acoes().stream())
                .filter(acao -> acao.status() == StatusAcaoPdi.CONCLUIDA)
                .count();
        return new PdiDetalheResponse(
                pdi.getId(),
                PessoaResumo.de(pdi.getColaborador()),
                PessoaResumo.de(pdi.getGestor()),
                pdi.getCiclo(),
                pdi.getInicio(),
                pdi.getFim(),
                pdi.getStatus(),
                PercentualPdi.de(concluidas, total),
                total,
                concluidas,
                pdi.getCriadoEm(),
                itens);
    }
}
