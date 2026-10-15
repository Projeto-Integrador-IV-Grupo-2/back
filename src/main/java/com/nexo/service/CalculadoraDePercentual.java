package com.nexo.service;

import org.springframework.stereotype.Component;

/**
 * Regra única do percentual de conclusão de uma trilha: etapas concluídas /
 * total de etapas, arredondado para baixo — 99,9% não pode aparecer como 100%
 * antes da última etapa. Singleton do Spring e sem estado.
 */
@Component
public class CalculadoraDePercentual {

    public int percentual(long concluidas, long total) {
        if (total <= 0) {
            return 0;
        }
        long limitadas = Math.min(Math.max(concluidas, 0), total);
        return (int) (limitadas * 100 / total);
    }

    /** Trilha com etapas e todas concluídas. Trilha sem etapas nunca se conclui sozinha. */
    public boolean completa(long concluidas, long total) {
        return total > 0 && concluidas >= total;
    }
}
