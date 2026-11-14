package com.nexo.service.recomendacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexo.exception.ParametroInvalidoException;
import com.nexo.repository.CriterioRecomendacao;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Factory Method da recomendação: o critério escolhe a estratégia. */
class EstrategiaRecomendacaoFactoryTest {

    @Test
    void entregaAEstrategiaDoCriterio() {
        EstrategiaRecomendacao porArea = new RecomendacaoPorArea();

        assertThat(new EstrategiaRecomendacaoFactory(List.of(porArea)).para(CriterioRecomendacao.AREA))
                .isSameAs(porArea);
    }

    @Test
    void criterioSemEstrategiaEhParametroInvalido() {
        EstrategiaRecomendacaoFactory fabrica = new EstrategiaRecomendacaoFactory(List.of(new RecomendacaoPorArea()));

        assertThatThrownBy(() -> fabrica.para(CriterioRecomendacao.IA))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void duasEstrategiasParaOMesmoCriterioImpedemASubida() {
        EstrategiaRecomendacao outra = mock(EstrategiaRecomendacao.class);
        when(outra.criterio()).thenReturn(CriterioRecomendacao.AREA);

        assertThatThrownBy(() -> new EstrategiaRecomendacaoFactory(List.of(new RecomendacaoPorArea(), outra)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AREA");
    }
}
