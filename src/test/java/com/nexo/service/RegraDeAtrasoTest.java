package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexo.entity.StatusAtribuicao;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RegraDeAtrasoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 5);

    private final RegraDeAtraso regra = new RegraDeAtraso(
            Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));

    @Test
    void prazoVencidoSemConclusaoEstaAtrasada() {
        assertThat(regra.statusEfetivo(StatusAtribuicao.EM_ANDAMENTO, HOJE.minusDays(1)))
                .isEqualTo(StatusAtribuicao.ATRASADA);
        assertThat(regra.statusEfetivo(StatusAtribuicao.NAO_INICIADA, HOJE.minusDays(1)))
                .isEqualTo(StatusAtribuicao.ATRASADA);
    }

    @Test
    void prazoDeHojeAindaNaoEstaAtrasado() {
        assertThat(regra.statusEfetivo(StatusAtribuicao.EM_ANDAMENTO, HOJE))
                .isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
    }

    @Test
    void semPrazoNuncaAtrasa() {
        assertThat(regra.estaAtrasada(StatusAtribuicao.EM_ANDAMENTO, null)).isFalse();
    }

    @Test
    void concluidaNuncaViraAtrasada() {
        assertThat(regra.statusEfetivo(StatusAtribuicao.CONCLUIDA, HOJE.minusDays(30)))
                .isEqualTo(StatusAtribuicao.CONCLUIDA);
    }

    @Test
    void atrasadaGravadaContinuaAtrasada() {
        assertThat(regra.estaAtrasada(StatusAtribuicao.ATRASADA, HOJE.plusDays(5))).isTrue();
    }
}
