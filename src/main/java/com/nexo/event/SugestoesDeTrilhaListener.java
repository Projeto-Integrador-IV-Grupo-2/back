package com.nexo.event;

import com.nexo.service.recomendacao.CacheDeSugestoes;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer que esquece as sugestões guardadas do usuário quando o que a IA
 * sabia sobre ele muda (issue #63).
 *
 * <p>Iniciar ou concluir uma trilha muda o catálogo de candidatas e o perfil
 * enviado ao modelo, então a sugestão guardada envelhece na hora. O módulo de
 * trilhas não precisa conhecer a sugestão: ele só publica o evento.</p>
 *
 * <p>Síncrono e sem esperar o commit de propósito: invalidar é barato e
 * repetível, e errar para o lado de recalcular é melhor do que mostrar uma
 * trilha que o usuário acabou de concluir.</p>
 */
@Component
public class SugestoesDeTrilhaListener {

    private final CacheDeSugestoes cache;

    public SugestoesDeTrilhaListener(CacheDeSugestoes cache) {
        this.cache = cache;
    }

    @EventListener
    public void aoIniciarTrilha(TrilhaIniciadaEvent evento) {
        cache.invalidar(evento.tenantId(), evento.usuarioId());
    }

    @EventListener
    public void aoConcluirTrilha(TrilhaConcluidaEvent evento) {
        cache.invalidar(evento.tenantId(), evento.usuarioId());
    }
}
