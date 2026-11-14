package com.nexo.event;

import com.nexo.service.recomendacao.CacheDeSugestoes;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Observer que esquece as sugestões guardadas quando o que a IA sabia muda
 * (issue #63). O módulo de trilhas não conhece a sugestão: ele só publica
 * os eventos.
 *
 * <ul>
 *   <li><strong>Trilha iniciada ou concluída:</strong> muda o perfil do
 *       usuário e o catálogo dele, então a sugestão <em>daquele usuário</em>
 *       envelhece.</li>
 *   <li><strong>Trilha inativada ou reativada:</strong> muda o catálogo de
 *       candidatas de todo mundo, então caem as sugestões de <em>todo o
 *       tenant</em>. Sem isso, uma trilha inativada continuaria sugerida até
 *       o cache vencer.</li>
 * </ul>
 *
 * <p><strong>Invalida duas vezes, de propósito:</strong> na hora, para a
 * própria requisição já recalcular, e de novo depois do commit. Se só
 * invalidasse na hora, uma requisição concorrente poderia recalcular com o
 * banco ainda sem a mudança e guardar uma sugestão velha. Invalidar é barato
 * e repetível, e se a transação voltar atrás o pior caso é uma chamada a mais
 * ao modelo.</p>
 */
@Component
public class SugestoesDeTrilhaListener {

    private final CacheDeSugestoes cache;

    public SugestoesDeTrilhaListener(CacheDeSugestoes cache) {
        this.cache = cache;
    }

    @EventListener
    public void aoIniciarTrilha(TrilhaIniciadaEvent evento) {
        agoraEDepoisDoCommit(() -> cache.invalidar(evento.tenantId(), evento.usuarioId()));
    }

    @EventListener
    public void aoConcluirTrilha(TrilhaConcluidaEvent evento) {
        agoraEDepoisDoCommit(() -> cache.invalidar(evento.tenantId(), evento.usuarioId()));
    }

    @EventListener
    public void aoInativarTrilha(TrilhaInativadaEvent evento) {
        agoraEDepoisDoCommit(() -> cache.invalidarTenant(evento.tenantId()));
    }

    @EventListener
    public void aoReativarTrilha(TrilhaReativadaEvent evento) {
        agoraEDepoisDoCommit(() -> cache.invalidarTenant(evento.tenantId()));
    }

    private static void agoraEDepoisDoCommit(Runnable invalidacao) {
        invalidacao.run();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidacao.run();
                }
            });
        }
    }
}
