package com.nexo.service.indexacao;

import com.nexo.event.ArquivoEnviadoEvent;
import com.nexo.event.ConteudoAtualizadoEvent;
import com.nexo.event.ConteudoDespublicadoEvent;
import com.nexo.event.ConteudoPublicadoEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer da indexação (issue #58): o cadastro de conteúdos só publica
 * eventos e não sabe que a indexação existe.
 *
 * <p>Roda depois do commit ({@code AFTER_COMMIT}) — nunca indexa um conteúdo
 * cuja transação voltou atrás — e fora da thread da requisição ({@code @Async}),
 * então o usuário não espera o provedor de IA e uma falha aqui não desfaz a
 * publicação.</p>
 */
@Component
public class IndexadorDeConteudoListener {

    private final IndexacaoService indexacaoService;

    public IndexadorDeConteudoListener(IndexacaoService indexacaoService) {
        this.indexacaoService = indexacaoService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoPublicar(ConteudoPublicadoEvent evento) {
        indexacaoService.indexar(evento.conteudoId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoEditar(ConteudoAtualizadoEvent evento) {
        indexacaoService.indexar(evento.conteudoId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoEnviarArquivo(ArquivoEnviadoEvent evento) {
        indexacaoService.indexar(evento.conteudoId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoDespublicar(ConteudoDespublicadoEvent evento) {
        indexacaoService.remover(evento.conteudoId());
    }
}
