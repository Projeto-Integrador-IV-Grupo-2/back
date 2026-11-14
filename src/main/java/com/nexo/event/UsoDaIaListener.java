package com.nexo.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer do uso da IA: transforma cada {@link ChamadaIaRealizadaEvent} em
 * métricas do Actuator, base para acompanhar uso e custo do provedor.
 *
 * <ul>
 *   <li>{@code nexo.ia.chamadas} (timer): quantidade e duração das chamadas;</li>
 *   <li>{@code nexo.ia.tokens} (contador): tokens cobrados, com
 *       {@code direcao=entrada|saida};</li>
 *   <li>{@code nexo.ia.embeddings.textos} (contador): textos enviados para embedding.</li>
 * </ul>
 *
 * <p>Todas levam as tags {@code provedor}, {@code modelo}, {@code operacao}
 * (e {@code sucesso}, no timer). Consulte em {@code /actuator/metrics}.</p>
 *
 * <p>Síncrono, porque só incrementa contadores em memória, e sem depender de
 * transação: boa parte das chamadas à IA acontece fora dela, de propósito.
 * Uma falha aqui é só registrada no log.</p>
 */
@Component
public class UsoDaIaListener {

    private static final Logger log = LoggerFactory.getLogger(UsoDaIaListener.class);

    private final MeterRegistry registry;

    public UsoDaIaListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @EventListener
    public void aoChamarIa(ChamadaIaRealizadaEvent evento) {
        try {
            String operacao = evento.operacao().name().toLowerCase(Locale.ROOT);
            Timer.builder("nexo.ia.chamadas")
                    .description("Chamadas ao provedor de IA")
                    .tag("provedor", evento.provedor())
                    .tag("modelo", evento.modelo())
                    .tag("operacao", operacao)
                    .tag("sucesso", String.valueOf(evento.sucesso()))
                    .register(registry)
                    .record(evento.duracao());
            contarTokens(evento, operacao, "entrada", evento.tokensEntrada());
            contarTokens(evento, operacao, "saida", evento.tokensSaida());
            if (evento.operacao() == ChamadaIaRealizadaEvent.Operacao.EMBEDDING && evento.itens() > 0) {
                Counter.builder("nexo.ia.embeddings.textos")
                        .description("Textos enviados para embedding")
                        .tag("provedor", evento.provedor())
                        .tag("modelo", evento.modelo())
                        .register(registry)
                        .increment(evento.itens());
            }
            log.debug("IA {} {} em {} ms, sucesso={}, tokens {}+{}", operacao, evento.modelo(),
                    evento.duracao().toMillis(), evento.sucesso(), evento.tokensEntrada(), evento.tokensSaida());
        } catch (RuntimeException ex) {
            log.warn("Falha ao registrar métricas de uso da IA.", ex);
        }
    }

    private void contarTokens(ChamadaIaRealizadaEvent evento, String operacao, String direcao, int tokens) {
        if (tokens <= 0) {
            return;
        }
        Counter.builder("nexo.ia.tokens")
                .description("Tokens cobrados pelo provedor de IA")
                .tag("provedor", evento.provedor())
                .tag("modelo", evento.modelo())
                .tag("operacao", operacao)
                .tag("direcao", direcao)
                .register(registry)
                .increment(tokens);
    }
}
