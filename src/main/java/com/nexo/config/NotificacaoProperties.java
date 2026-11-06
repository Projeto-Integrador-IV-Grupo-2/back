package com.nexo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Notificações, lidas de {@code app.notificacoes.*} (issue #78).
 *
 * @param diasAntesDoPrazo com quantos dias de antecedência sai o aviso de prazo próximo
 *                         ({@code NOTIFICACAO_DIAS_PRAZO}, padrão 3)
 * @param retencaoLidas    depois de quanto tempo de lidas as notificações são apagadas
 *                         ({@code NOTIFICACAO_RETENCAO_LIDAS}, padrão 90 dias)
 */
@ConfigurationProperties(prefix = "app.notificacoes")
public record NotificacaoProperties(Integer diasAntesDoPrazo, Duration retencaoLidas) {

    public NotificacaoProperties {
        diasAntesDoPrazo = diasAntesDoPrazo == null ? 3 : diasAntesDoPrazo;
        retencaoLidas = retencaoLidas == null ? Duration.ofDays(90) : retencaoLidas;
    }

    /** Liga o record acima ao contexto. */
    @Configuration
    @EnableConfigurationProperties(NotificacaoProperties.class)
    static class Config {
    }
}
