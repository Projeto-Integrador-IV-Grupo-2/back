package com.nexo.dto.atribuicao;

import com.nexo.entity.StatusAtribuicao;
import java.util.Collection;

/** Quantas trilhas o colaborador tem em cada status efetivo. */
public record TotaisPorStatus(
        long total,
        long naoIniciadas,
        long emAndamento,
        long concluidas,
        long atrasadas) {

    /** Conta a partir das linhas já com o status efetivo, para os totais baterem com a lista. */
    public static TotaisPorStatus de(Collection<AtribuicaoAcompanhamentoResponse> trilhas) {
        return new TotaisPorStatus(
                trilhas.size(),
                contar(trilhas, StatusAtribuicao.NAO_INICIADA),
                contar(trilhas, StatusAtribuicao.EM_ANDAMENTO),
                contar(trilhas, StatusAtribuicao.CONCLUIDA),
                contar(trilhas, StatusAtribuicao.ATRASADA));
    }

    private static long contar(Collection<AtribuicaoAcompanhamentoResponse> trilhas, StatusAtribuicao status) {
        return trilhas.stream().filter(trilha -> trilha.status() == status).count();
    }
}
