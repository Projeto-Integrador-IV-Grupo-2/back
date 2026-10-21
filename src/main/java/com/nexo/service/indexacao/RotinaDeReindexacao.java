package com.nexo.service.indexacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nova tentativa das indexações que falharam (issue #58).
 *
 * <p>Só decide <em>quando</em> rodar; quais falhas reprocessar e com que
 * espera entre elas é do {@link IndexacaoService#reprocessarFalhas()}.</p>
 */
@Component
public class RotinaDeReindexacao {

    private static final Logger log = LoggerFactory.getLogger(RotinaDeReindexacao.class);

    private final IndexacaoService indexacaoService;

    public RotinaDeReindexacao(IndexacaoService indexacaoService) {
        this.indexacaoService = indexacaoService;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${app.ia.indexacao.intervalo-rotina:PT1M}")
    public void reprocessar() {
        int reprocessadas = indexacaoService.reprocessarFalhas();
        if (reprocessadas > 0) {
            log.info("Rotina de reindexação reprocessou {} conteúdo(s)", reprocessadas);
        }
    }
}
