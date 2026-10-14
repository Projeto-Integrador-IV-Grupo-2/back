package com.nexo.dto.pdi;

/**
 * Percentual do PDI = ações concluídas / total de ações. Sem ações, 0%.
 * Derivado na resposta, nunca persistido.
 */
final class PercentualPdi {

    private PercentualPdi() {
    }

    static int de(long concluidas, long total) {
        return total == 0 ? 0 : (int) Math.round(concluidas * 100.0 / total);
    }
}
