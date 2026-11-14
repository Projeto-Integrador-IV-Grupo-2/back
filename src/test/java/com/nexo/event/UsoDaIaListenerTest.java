package com.nexo.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexo.event.ChamadaIaRealizadaEvent.Operacao;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Observer do uso da IA: cada chamada vira métrica de quantidade, duração e tokens. */
class UsoDaIaListenerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final UsoDaIaListener listener = new UsoDaIaListener(registry);

    @Test
    void geracaoContaChamadaETokensDeEntradaESaida() {
        listener.aoChamarIa(new ChamadaIaRealizadaEvent("gemini", "flash", Operacao.GERACAO, 100, 20, 1,
                Duration.ofMillis(300), true));
        listener.aoChamarIa(new ChamadaIaRealizadaEvent("gemini", "flash", Operacao.GERACAO, 50, 10, 1,
                Duration.ofMillis(100), true));

        assertThat(registry.get("nexo.ia.chamadas").tag("operacao", "geracao").tag("sucesso", "true")
                .timer().count()).isEqualTo(2);
        assertThat(registry.get("nexo.ia.tokens").tag("direcao", "entrada").counter().count()).isEqualTo(150);
        assertThat(registry.get("nexo.ia.tokens").tag("direcao", "saida").counter().count()).isEqualTo(30);
    }

    @Test
    void embeddingContaOsTextosEnviados() {
        listener.aoChamarIa(new ChamadaIaRealizadaEvent("gemini", "vetor", Operacao.EMBEDDING, 0, 0, 8,
                Duration.ofMillis(50), true));

        assertThat(registry.get("nexo.ia.embeddings.textos").counter().count()).isEqualTo(8);
        assertThat(registry.find("nexo.ia.tokens").counter()).isNull();
    }

    @Test
    void falhaEntraComoChamadaSemSucesso() {
        listener.aoChamarIa(new ChamadaIaRealizadaEvent("gemini", "flash", Operacao.GERACAO, 0, 0, 1,
                Duration.ofMillis(20), false));

        assertThat(registry.get("nexo.ia.chamadas").tag("sucesso", "false").timer().count()).isEqualTo(1);
    }
}
