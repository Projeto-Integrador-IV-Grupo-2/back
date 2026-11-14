package com.nexo.service.assistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexo.entity.ContextoChat;
import com.nexo.exception.OperacaoNaoPermitidaException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Factory Method do prompt: o contexto da sessão escolhe o montador. */
class MontadorDePromptFactoryTest {

    @Test
    void entregaOMontadorDoContexto() {
        MontadorDePrompt conhecimento = new MontadorDePromptConhecimento();

        assertThat(new MontadorDePromptFactory(List.of(conhecimento)).para(ContextoChat.CONHECIMENTO))
                .isSameAs(conhecimento);
    }

    @Test
    void contextoSemMontadorRespondeConflito() {
        MontadorDePromptFactory fabrica = new MontadorDePromptFactory(List.of(new MontadorDePromptConhecimento()));

        assertThatThrownBy(() -> fabrica.para(ContextoChat.TRILHAS))
                .isInstanceOf(OperacaoNaoPermitidaException.class);
    }

    @Test
    void doisMontadoresParaOMesmoContextoImpedemASubida() {
        MontadorDePrompt outro = mock(MontadorDePrompt.class);
        when(outro.contexto()).thenReturn(ContextoChat.CONHECIMENTO);

        assertThatThrownBy(() -> new MontadorDePromptFactory(List.of(new MontadorDePromptConhecimento(), outro)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONHECIMENTO");
    }
}
