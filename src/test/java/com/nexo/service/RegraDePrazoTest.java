package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.TrilhaAtribuicao;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * Regra de atraso com a data sob controle: o {@link Clock} é fixado em
 * 2026-06-15, então os casos de prazo vencido, prazo de hoje e prazo futuro
 * valem em qualquer dia em que a suíte rodar.
 */
class RegraDePrazoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 6, 15);

    private final RegraDePrazo regra = new RegraDePrazo(
            Clock.fixed(ZonedDateTime.of(HOJE.atStartOfDay(), ZoneId.systemDefault()).toInstant(),
                    ZoneId.systemDefault()));

    @Test
    void prazoVencidoComTrilhaEmAndamentoEstaAtrasada() {
        TrilhaAtribuicao atribuicao = atribuicao(HOJE.minusDays(1), StatusAtribuicao.EM_ANDAMENTO);

        assertThat(regra.estaAtrasada(atribuicao)).isTrue();
        assertThat(regra.statusEfetivo(atribuicao)).isEqualTo(StatusAtribuicao.ATRASADA);
    }

    @Test
    void prazoVencidoComTrilhaNaoIniciadaEstaAtrasada() {
        assertThat(regra.estaAtrasada(atribuicao(HOJE.minusDays(30), StatusAtribuicao.NAO_INICIADA))).isTrue();
    }

    @Test
    void prazoQueVenceHojeAindaNaoEstaAtrasada() {
        assertThat(regra.estaAtrasada(atribuicao(HOJE, StatusAtribuicao.EM_ANDAMENTO))).isFalse();
    }

    @Test
    void prazoFuturoNaoEstaAtrasada() {
        assertThat(regra.estaAtrasada(atribuicao(HOJE.plusDays(1), StatusAtribuicao.EM_ANDAMENTO))).isFalse();
    }

    @Test
    void trilhaConcluidaNuncaFicaAtrasadaMesmoComPrazoVencido() {
        TrilhaAtribuicao concluida = atribuicao(HOJE.minusDays(10), StatusAtribuicao.CONCLUIDA);

        assertThat(regra.estaAtrasada(concluida)).isFalse();
        assertThat(regra.statusEfetivo(concluida)).isEqualTo(StatusAtribuicao.CONCLUIDA);
    }

    @Test
    void atribuicaoSemPrazoNuncaFicaAtrasada() {
        assertThat(regra.estaAtrasada(atribuicao(null, StatusAtribuicao.EM_ANDAMENTO))).isFalse();
    }

    @Test
    void statusEfetivoPreservaOGravadoQuandoNaoHaAtraso() {
        TrilhaAtribuicao emDia = atribuicao(HOJE.plusDays(5), StatusAtribuicao.EM_ANDAMENTO);

        assertThat(regra.statusEfetivo(emDia)).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
    }

    @Test
    void hojeVemDoRelogioInjetado() {
        assertThat(regra.hoje()).isEqualTo(HOJE);
    }

    private TrilhaAtribuicao atribuicao(LocalDate prazo, StatusAtribuicao status) {
        TrilhaAtribuicao atribuicao = new TrilhaAtribuicao();
        atribuicao.setPrazo(prazo);
        atribuicao.setStatus(status);
        return atribuicao;
    }
}
