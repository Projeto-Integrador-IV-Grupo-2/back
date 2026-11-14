package com.nexo.event;

import com.nexo.service.HistoricoInteracaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
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

    /**
     * Consulta ao assistente (issue #59).
     *
     * <p>Aqui é {@code @EventListener} simples, e não
     * {@code @TransactionalEventListener}: o {@code ConsultaAssistenteService}
     * publica este evento <strong>fora de transação</strong>, de propósito (a
     * chamada ao modelo não pode segurar conexão do banco). Um listener
     * ligado ao commit nunca dispararia, e a gravação sumiria em silêncio. O
     * {@code registrar} abre a própria transação ({@code REQUIRES_NEW}).</p>
     */
    @EventListener
    public void aoResponderPergunta(PerguntaRespondidaEvent evento) {
        tentarRegistrar("ASSISTENTE_CONSULTADO", evento.sessaoId(), () -> historicoService.registrar(evento));
    }

    /** Senha redefinida pelo link do e-mail (issue #76). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRedefinirSenha(SenhaRedefinidaEvent evento) {
        tentarRegistrar("SENHA_REDEFINIDA", evento.usuarioId(), () -> historicoService.registrar(evento));
    }

    /** Senha trocada pela tela de perfil. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoTrocarSenha(SenhaTrocadaEvent evento) {
        tentarRegistrar("SENHA_TROCADA", evento.usuarioId(), () -> historicoService.registrar(evento));
    }

    private void tentarRegistrar(String tipo, java.util.UUID referencia, Runnable gravacao) {
        try {
            gravacao.run();
        } catch (RuntimeException ex) {
            log.error("Falha ao gravar {} no histórico para {}. A operação principal foi preservada.",
                    tipo, referencia, ex);
        }
    }
}
