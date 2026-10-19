package com.nexo.dto;

import com.nexo.entity.StatusAtribuicao;
import java.util.Collection;

/** Contagens do progresso efetivo das atribuições. */
public record ResumoStatusDashboard(
        long total,
        long naoIniciadas,
        long emAndamento,
        long concluidas,
        long atrasadas) {

    public static ResumoStatusDashboard de(Collection<StatusAtribuicao> status) {
        return new ResumoStatusDashboard(
                status.size(),
                contar(status, StatusAtribuicao.NAO_INICIADA),
                contar(status, StatusAtribuicao.EM_ANDAMENTO),
                contar(status, StatusAtribuicao.CONCLUIDA),
                contar(status, StatusAtribuicao.ATRASADA));
    }

    public static ResumoStatusDashboard vazio() {
        return new ResumoStatusDashboard(0, 0, 0, 0, 0);
    }

    private static long contar(Collection<StatusAtribuicao> status, StatusAtribuicao procurado) {
        return status.stream().filter(item -> item == procurado).count();
    }
}
