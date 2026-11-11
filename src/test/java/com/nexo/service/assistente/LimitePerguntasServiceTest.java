package com.nexo.service.assistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.exception.LimiteDePerguntasExcedidoException;
import com.nexo.repository.PerguntaRateLimitRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitePerguntasServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private final OffsetDateTime agora = OffsetDateTime.parse("2026-10-26T12:00:00Z");

    @Mock
    private PerguntaRateLimitRepository repository;

    private LimitePerguntasService service;

    @BeforeEach
    void prepara() {
        service = new LimitePerguntasService(repository);
    }

    @Test
    void reservaUmaTentativaQuandoAindaHaQuota() {
        when(repository.agoraDoBanco()).thenReturn(agora);
        when(repository.contarDesde(tenantId, usuarioId, agora.minusSeconds(60))).thenReturn(2L);

        service.reservar(tenantId, usuarioId, 3);

        verify(repository).garantirETravar(tenantId, usuarioId);
        verify(repository).agoraDoBanco();
        verify(repository).removerAntigas(tenantId, usuarioId, agora.minusSeconds(60));
        verify(repository).registrar(tenantId, usuarioId, agora);
    }

    @Test
    void recusaQuandoAQuotaEstaCheiaECalculaRetryAfter() {
        when(repository.agoraDoBanco()).thenReturn(agora);
        when(repository.contarDesde(tenantId, usuarioId, agora.minusSeconds(60))).thenReturn(3L);
        when(repository.buscarMaisAntigaDesde(tenantId, usuarioId, agora.minusSeconds(60)))
                .thenReturn(agora.minusSeconds(20));

        assertThatThrownBy(() -> service.reservar(tenantId, usuarioId, 3))
                .isInstanceOfSatisfying(LimiteDePerguntasExcedidoException.class,
                        ex -> assertThat(ex.getSegundosParaTentarDeNovo()).isEqualTo(40));
        verify(repository, never()).registrar(tenantId, usuarioId, agora);
    }
}
