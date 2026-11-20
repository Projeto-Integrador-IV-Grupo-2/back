package com.nexo.dto.pdi;

import com.nexo.entity.StatusAcaoPdi;
import com.nexo.entity.StatusAtribuicao;
import java.util.List;

/**
 * Percentual do PDI: média do avanço de cada ação. Ação de trilha conta o
 * percentual do colaborador na trilha (sobe conforme ele estuda); as demais
 * contam 100% quando concluídas. Sem ações, 0%. Derivado na resposta, nunca
 * persistido.
 */
final class PercentualPdi {

    private PercentualPdi() {
    }

    static int de(long concluidas, long total) {
        return total == 0 ? 0 : (int) Math.round(concluidas * 100.0 / total);
    }

    static int de(List<PdiAcaoResponse> acoes) {
        if (acoes.isEmpty()) {
            return 0;
        }
        double soma = acoes.stream().mapToDouble(PercentualPdi::avanco).sum();
        return (int) Math.round(soma / acoes.size());
    }

    /** Concluída: a ação marcada como concluída ou a trilha já terminada. */
    static boolean concluida(PdiAcaoResponse acao) {
        return acao.status() == StatusAcaoPdi.CONCLUIDA
                || (acao.progressoTrilha() != null && acao.progressoTrilha().status() == StatusAtribuicao.CONCLUIDA);
    }

    private static double avanco(PdiAcaoResponse acao) {
        if (concluida(acao)) {
            return 100;
        }
        return acao.progressoTrilha() != null ? acao.progressoTrilha().percentual() : 0;
    }
}
