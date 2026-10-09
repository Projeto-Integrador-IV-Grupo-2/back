package com.nexo.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação. Regras que dependem de "hoje" (como prazo
 * vencido) recebem este bean em vez de chamar {@code LocalDate.now()}, para os
 * testes poderem fixar a data.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
