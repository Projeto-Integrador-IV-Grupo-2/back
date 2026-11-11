package com.nexo.service.notificacao;

import com.nexo.event.AtribuicaoAtrasadaEvent;
import com.nexo.event.ConteudoPublicadoEvent;
import com.nexo.event.TrilhaAtribuidaEvent;
import com.nexo.event.TrilhaConcluidaEvent;
import com.nexo.event.TrilhaInativadaEvent;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer das notificações (issue #78): escuta os eventos que o sistema já
 * publica e transforma em avisos. Os serviços de trilha e de conteúdo não
 * sabem que notificação existe.
 *
 * <p>Depois do commit ({@code AFTER_COMMIT}) e em outra thread
 * ({@code @Async}): a ação que causou o aviso já está confirmada e não espera
 * pela notificação. Uma falha aqui só vai para o log — nunca desfaz a ação.
 * {@code fallbackExecution} cobre evento publicado fora de transação.</p>
 */
@Component
public class NotificacoesListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacoesListener.class);

    private final GeradorDeNotificacoes gerador;

    public NotificacoesListener(GeradorDeNotificacoes gerador) {
        this.gerador = gerador;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoAtribuir(TrilhaAtribuidaEvent evento) {
        gerar("TRILHA_ATRIBUIDA", () -> gerador.aoAtribuir(evento));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoAtrasar(AtribuicaoAtrasadaEvent evento) {
        gerar("PRAZO_VENCIDO", () -> gerador.aoAtrasar(evento));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoConcluir(TrilhaConcluidaEvent evento) {
        gerar("TRILHA_CONCLUIDA", () -> gerador.aoConcluir(evento));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoPublicar(ConteudoPublicadoEvent evento) {
        gerar("CONTEUDO_PUBLICADO", () -> gerador.aoPublicar(evento));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoInativar(TrilhaInativadaEvent evento) {
        gerar("TRILHA_INATIVADA", () -> gerador.aoInativar(evento));
    }

    private void gerar(String tipo, Supplier<Integer> geracao) {
        try {
            geracao.get();
        } catch (RuntimeException ex) {
            log.error("Falha ao gerar notificações {}. A ação que as causou foi preservada.", tipo, ex);
        }
    }
}
