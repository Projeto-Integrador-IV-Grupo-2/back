package com.nexo.service;

import com.nexo.config.ClockConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agendamento da marcação de atribuições atrasadas.
 *
 * <p>Só decide <em>quando</em> rodar; o que fazer é do
 * {@link TrilhaAtribuicaoService#marcarAtrasadas()}. Essa separação é o que
 * permite testar a regra sem esperar o relógio.</p>
 *
 * <p>Roda às 3h no fuso de São Paulo — o mesmo do {@code Clock}, para a rotina
 * e a regra nunca discordarem sobre que dia é hoje. A leitura não depende dela — o
 * {@link RegraDeAtraso} marca o atraso na resposta a qualquer hora; a rotina
 * existe para o status ficar persistido e consultável.</p>
 */
@Component
public class RotinaDeAtraso {

    private static final Logger log = LoggerFactory.getLogger(RotinaDeAtraso.class);

    private final TrilhaAtribuicaoService trilhaAtribuicaoService;

    public RotinaDeAtraso(TrilhaAtribuicaoService trilhaAtribuicaoService) {
        this.trilhaAtribuicaoService = trilhaAtribuicaoService;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = ClockConfig.FUSO_ID)
    public void marcarAtrasadas() {
        int marcadas = trilhaAtribuicaoService.marcarAtrasadas();
        if (marcadas > 0) {
            log.info("Rotina de atraso marcou {} atribuições como ATRASADA", marcadas);
        }
    }
}
