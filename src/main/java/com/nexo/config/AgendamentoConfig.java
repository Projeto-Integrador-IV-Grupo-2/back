package com.nexo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as rotinas agendadas da aplicação.
 *
 * <p>Sem isto, um {@code @Scheduled} é ignorado <strong>em silêncio</strong> —
 * o método existe, compila, tem teste de unidade passando e nunca roda. Hoje a
 * única rotina é a {@code RotinaDeAtraso}, que persiste o status das
 * atribuições vencidas.</p>
 */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
