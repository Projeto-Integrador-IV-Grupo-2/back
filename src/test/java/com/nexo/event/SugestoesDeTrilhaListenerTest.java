package com.nexo.event;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.nexo.service.recomendacao.CacheDeSugestoes;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Observer que esquece as sugestões quando uma trilha muda (issue #63). */
@ExtendWith(MockitoExtension.class)
class SugestoesDeTrilhaListenerTest {

    @Mock
    private CacheDeSugestoes cache;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();

    @AfterEach
    void limparSincronizacao() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void iniciarUmaTrilhaInvalidaAsSugestoesDoUsuario() {
        new SugestoesDeTrilhaListener(cache)
                .aoIniciarTrilha(new TrilhaIniciadaEvent(UUID.randomUUID(), "Trilha", tenantId, usuarioId));

        verify(cache).invalidar(tenantId, usuarioId);
    }

    @Test
    void concluirUmaTrilhaInvalidaAsSugestoesDoUsuario() {
        new SugestoesDeTrilhaListener(cache).aoConcluirTrilha(
                new TrilhaConcluidaEvent(tenantId, UUID.randomUUID(), usuarioId, OffsetDateTime.now()));

        verify(cache).invalidar(tenantId, usuarioId);
    }

    @Test
    void inativarUmaTrilhaInvalidaAsSugestoesDoTenantInteiro() {
        new SugestoesDeTrilhaListener(cache)
                .aoInativarTrilha(new TrilhaInativadaEvent(UUID.randomUUID(), tenantId, usuarioId));

        verify(cache).invalidarTenant(tenantId);
    }

    @Test
    void reativarUmaTrilhaInvalidaAsSugestoesDoTenantInteiro() {
        new SugestoesDeTrilhaListener(cache)
                .aoReativarTrilha(new TrilhaReativadaEvent(UUID.randomUUID(), tenantId, usuarioId));

        verify(cache).invalidarTenant(tenantId);
    }

    @Test
    void dentroDeTransacaoInvalidaNaHoraEDeNovoDepoisDoCommit() {
        TransactionSynchronizationManager.initSynchronization();

        new SugestoesDeTrilhaListener(cache)
                .aoIniciarTrilha(new TrilhaIniciadaEvent(UUID.randomUUID(), "Trilha", tenantId, usuarioId));
        verify(cache).invalidar(tenantId, usuarioId);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(cache, times(2)).invalidar(tenantId, usuarioId);
        verifyNoMoreInteractions(cache);
    }
}
