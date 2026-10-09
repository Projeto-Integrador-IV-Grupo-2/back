package com.nexo.service;

import com.nexo.entity.StatusAtribuicao;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Regra única de "trilha atrasada": prazo vencido e não concluída. Um bean só
 * (singleton do Spring) para a regra não ter duas versões — o status efetivo
 * calculado aqui em Java é o mesmo que as consultas aplicam no banco (ver
 * {@code AtribuicaoSpecifications.statusEfetivo}).
 */
@Component
public class RegraDeAtraso {

    private final Clock clock;

    public RegraDeAtraso(Clock clock) {
        this.clock = clock;
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    public boolean estaAtrasada(StatusAtribuicao status, LocalDate prazo) {
        if (status == StatusAtribuicao.CONCLUIDA) {
            return false;
        }
        if (status == StatusAtribuicao.ATRASADA) {
            return true;
        }
        return prazo != null && prazo.isBefore(hoje());
    }

    /** Status gravado, ou ATRASADA quando o prazo já venceu sem conclusão. */
    public StatusAtribuicao statusEfetivo(StatusAtribuicao status, LocalDate prazo) {
        return estaAtrasada(status, prazo) ? StatusAtribuicao.ATRASADA : status;
    }
}
