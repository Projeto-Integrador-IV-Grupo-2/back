package com.nexo.event;

import com.nexo.service.HistoricoInteracaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener de auditoria desacoplado dos services de trilha.
 * Executa somente após o commit e absorve erros de persistência para nunca
 * converter uma operação de trilha concluída em falha da requisição HTTP.
 */
@Component
public class HistoricoInteracaoListener {

    private static final Logger log = LoggerFactory.getLogger(HistoricoInteracaoListener.class);
    private final HistoricoInteracaoService historicoService;

    public HistoricoInteracaoListener(HistoricoInteracaoService historicoService) {
        this.historicoService = historicoService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAcessarTrilha(TrilhaAcessadaEvent evento) {
        tentarRegistrar("TRILHA_ACESSADA", evento.trilhaId(), () -> historicoService.registrar(evento));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoIniciarTrilha(TrilhaIniciadaEvent evento) {
        tentarRegistrar("TRILHA_INICIADA", evento.trilhaId(), () -> historicoService.registrar(evento));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoConcluirTrilha(TrilhaConcluidaEvent evento) {
        tentarRegistrar("TRILHA_CONCLUIDA", evento.trilhaId(), () -> historicoService.registrar(evento));
    }

    private void tentarRegistrar(String tipo, java.util.UUID trilhaId, Runnable gravacao) {
        try {
            gravacao.run();
        } catch (RuntimeException ex) {
            log.error("Falha ao gravar {} no histórico para a trilha {}. A operação principal foi preservada.",
                    tipo, trilhaId, ex);
        }
    }
}
