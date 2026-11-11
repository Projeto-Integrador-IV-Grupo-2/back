package com.nexo.service.notificacao;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.event.TrilhaAtribuidaEvent;
import com.nexo.event.TrilhaInativadaEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificacoesListenerTest {

    @Mock
    private GeradorDeNotificacoes gerador;

    @Test
    void repassaOEventoParaOGerador() {
        TrilhaAtribuidaEvent evento = new TrilhaAtribuidaEvent(UUID.randomUUID(), UUID.randomUUID(),
                List.of(UUID.randomUUID()), UUID.randomUUID());

        new NotificacoesListener(gerador).aoAtribuir(evento);

        verify(gerador).aoAtribuir(evento);
    }

    @Test
    void falhaAoGerarNuncaSobeParaQuemCausouOEvento() {
        TrilhaInativadaEvent evento = new TrilhaInativadaEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(gerador.aoInativar(evento)).thenThrow(new IllegalStateException("banco fora do ar"));

        assertThatCode(() -> new NotificacoesListener(gerador).aoInativar(evento)).doesNotThrowAnyException();
    }
}
