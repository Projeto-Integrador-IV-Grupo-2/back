package com.nexo.service.assistente;

import com.nexo.exception.LimiteDePerguntasExcedidoException;
import com.nexo.repository.PerguntaRateLimitRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reserva quotas de perguntas com consistência entre requisições e instâncias da API. */
@Service
public class LimitePerguntasService {

    private static final long JANELA_SEGUNDOS = 60;
    private final PerguntaRateLimitRepository repository;

    public LimitePerguntasService(PerguntaRateLimitRepository repository) {
        this.repository = repository;
    }

    /**
     * Reserva uma tentativa dentro de uma janela móvel de 60 segundos. A reserva é
     * confirmada antes de chamar o provedor; assim chamadas concorrentes não passam
     * juntas do limite e falhas do provedor também consomem quota.
     */
    @Transactional
    public void reservar(UUID tenantId, UUID usuarioId, int limite) {
        repository.garantirETravar(tenantId, usuarioId);
        // Obtém o instante após o bloqueio, usando o mesmo relógio do banco em
        // todas as instâncias. Isso evita janelas divergentes por clock skew.
        OffsetDateTime agora = repository.agoraDoBanco();
        OffsetDateTime corte = agora.minusSeconds(JANELA_SEGUNDOS);

        if (repository.contarDesde(tenantId, usuarioId, corte) >= limite) {
            OffsetDateTime maisAntiga = repository.buscarMaisAntigaDesde(tenantId, usuarioId, corte);
            long retryAfter = segundosAteLiberar(agora, maisAntiga);
            throw new LimiteDePerguntasExcedidoException(
                    "Você fez muitas perguntas em pouco tempo. Aguarde e tente novamente.", retryAfter);
        }

        // Faz a limpeza apenas quando a reserva será aceita; se o limite for
        // excedido, a exceção causa rollback e não vale a pena emitir um DELETE
        // que inevitavelmente será desfeito.
        repository.removerAntigas(tenantId, usuarioId, corte);
        repository.registrar(tenantId, usuarioId, agora);
    }

    private long segundosAteLiberar(OffsetDateTime agora, OffsetDateTime maisAntiga) {
        if (maisAntiga == null) {
            return JANELA_SEGUNDOS;
        }
        long millis = Duration.between(agora, maisAntiga.plusSeconds(JANELA_SEGUNDOS)).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}
