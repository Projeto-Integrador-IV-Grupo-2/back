package com.nexo.service.notificacao;

import com.nexo.config.ClockConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotina diária das notificações (issue #78): avisa os prazos que vencem nos
 * próximos dias e apaga as notificações lidas há mais de 90 dias.
 *
 * <p>Só decide <em>quando</em> rodar; o que fazer é do
 * {@link GeradorDeNotificacoes}, para dar para testar sem esperar o relógio.
 * Roda às 7h no fuso de São Paulo, depois da rotina de atraso (3h), para o
 * aviso de prazo próximo não pegar quem já virou atrasado.</p>
 */
@Component
public class RotinaDeNotificacoes {

    private static final Logger log = LoggerFactory.getLogger(RotinaDeNotificacoes.class);

    private final GeradorDeNotificacoes gerador;

    public RotinaDeNotificacoes(GeradorDeNotificacoes gerador) {
        this.gerador = gerador;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = ClockConfig.FUSO_ID)
    public void rodar() {
        try {
            int avisos = gerador.avisarPrazosProximos();
            if (avisos > 0) {
                log.info("Rotina de notificações: {} avisos de prazo", avisos);
            }
        } catch (RuntimeException ex) {
            log.error("Falha ao gerar avisos de prazo próximo", ex);
        }

        try {
            int apagadas = gerador.apagarLidasAntigas();
            if (apagadas > 0) {
                log.info("Rotina de notificações: {} notificações lidas antigas apagadas", apagadas);
            }
        } catch (RuntimeException ex) {
            log.error("Falha ao apagar notificações lidas antigas", ex);
        }
    }
}
