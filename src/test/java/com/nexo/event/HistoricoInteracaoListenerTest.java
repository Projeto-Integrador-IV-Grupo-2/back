package com.nexo.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.nexo.service.HistoricoInteracaoService;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class HistoricoInteracaoListenerTest {

    @Mock
    private HistoricoInteracaoService historicoService;

    @InjectMocks
    private HistoricoInteracaoListener listener;

    @Test
    void falhaAoGravarHistoricoNaoEscapaDoListenerNemDaOperacaoPrincipal() {
        TrilhaAcessadaEvent evento = new TrilhaAcessadaEvent(
                UUID.randomUUID(), "Onboarding", UUID.randomUUID(), UUID.randomUUID());
        doThrow(new IllegalStateException("banco indisponível")).when(historicoService).registrar(evento);

        assertThatCode(() -> listener.aoAcessarTrilha(evento)).doesNotThrowAnyException();
        verify(historicoService).registrar(evento);
    }

    @Test
    void perguntaRespondidaVaiParaOHistorico() {
        PerguntaRespondidaEvent evento = eventoDePergunta();

        listener.aoResponderPergunta(evento);

        verify(historicoService).registrar(evento);
    }

    @Test
    void falhaAoGravarAConsultaAoAssistenteTambemNaoEscapa() {
        PerguntaRespondidaEvent evento = eventoDePergunta();
        doThrow(new IllegalStateException("banco indisponível")).when(historicoService).registrar(evento);

        assertThatCode(() -> listener.aoResponderPergunta(evento)).doesNotThrowAnyException();
    }

    /**
     * O evento do assistente é publicado fora de transação, então o listener
     * dele precisa ser {@code @EventListener} simples. Com
     * {@code @TransactionalEventListener} a gravação sumiria em silêncio.
     */
    @Test
    void oListenerDoAssistenteNaoDependeDeCommit() throws Exception {
        Method metodo = HistoricoInteracaoListener.class.getMethod(
                "aoResponderPergunta", PerguntaRespondidaEvent.class);

        assertThat(metodo.isAnnotationPresent(EventListener.class)).isTrue();
        assertThat(metodo.isAnnotationPresent(TransactionalEventListener.class)).isFalse();
    }

    private static PerguntaRespondidaEvent eventoDePergunta() {
        return new PerguntaRespondidaEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), true, 2);
    }
}
