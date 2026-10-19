package com.nexo.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.nexo.service.HistoricoInteracaoService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
