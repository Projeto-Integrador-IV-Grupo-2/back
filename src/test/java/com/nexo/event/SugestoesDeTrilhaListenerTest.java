package com.nexo.event;

import static org.mockito.Mockito.verify;

import com.nexo.service.recomendacao.CacheDeSugestoes;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Observer que esquece as sugestões quando o usuário mexe numa trilha (issue #63). */
@ExtendWith(MockitoExtension.class)
class SugestoesDeTrilhaListenerTest {

    @Mock
    private CacheDeSugestoes cache;

    @Test
    void iniciarUmaTrilhaInvalidaAsSugestoesDoUsuario() {
        UUID tenantId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        new SugestoesDeTrilhaListener(cache)
                .aoIniciarTrilha(new TrilhaIniciadaEvent(UUID.randomUUID(), "Trilha", tenantId, usuarioId));

        verify(cache).invalidar(tenantId, usuarioId);
    }

    @Test
    void concluirUmaTrilhaInvalidaAsSugestoesDoUsuario() {
        UUID tenantId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        new SugestoesDeTrilhaListener(cache).aoConcluirTrilha(
                new TrilhaConcluidaEvent(tenantId, UUID.randomUUID(), usuarioId, OffsetDateTime.now()));

        verify(cache).invalidar(tenantId, usuarioId);
    }
}
