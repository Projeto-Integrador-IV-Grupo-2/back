package com.nexo.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Fonte de tempo da aplicação e habilitação das rotinas agendadas.
 *
 * <p>O {@link Clock} existe para que "hoje" seja injetável: as regras de prazo
 * nunca chamam {@code LocalDate.now()} direto, então os testes conseguem fixar
 * a data (ver {@code RegraDePrazoTest}) em vez de depender do dia em que a
 * suíte roda.</p>
 */
@Configuration
@EnableScheduling
public class TempoConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
