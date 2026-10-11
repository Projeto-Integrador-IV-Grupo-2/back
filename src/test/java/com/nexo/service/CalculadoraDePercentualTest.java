package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CalculadoraDePercentualTest {

    private final CalculadoraDePercentual calculadora = new CalculadoraDePercentual();

    @Test
    void percentualEhConcluidasSobreTotalArredondadoParaBaixo() {
        assertThat(calculadora.percentual(1, 3)).isEqualTo(33);
        assertThat(calculadora.percentual(2, 3)).isEqualTo(66);
        assertThat(calculadora.percentual(3, 3)).isEqualTo(100);
    }

    @Test
    void trilhaSemEtapasFicaEmZeroENuncaSeCompleta() {
        assertThat(calculadora.percentual(0, 0)).isZero();
        assertThat(calculadora.completa(0, 0)).isFalse();
    }

    @Test
    void soCompletaQuandoTodasAsEtapasForamConcluidas() {
        assertThat(calculadora.completa(2, 3)).isFalse();
        assertThat(calculadora.completa(3, 3)).isTrue();
    }

    @Test
    void nuncaPassaDeCem() {
        assertThat(calculadora.percentual(5, 3)).isEqualTo(100);
    }
}
