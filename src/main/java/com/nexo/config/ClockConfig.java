package com.nexo.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação. Regras que dependem de "hoje" (como prazo
 * vencido) recebem este bean em vez de chamar {@code LocalDate.now()}, para os
 * testes poderem fixar a data.
 *
 * <p>O fuso é fixado em {@code America/Sao_Paulo} de propósito: com o fuso do
 * servidor, a mesma atribuição poderia estar atrasada ou não dependendo de
 * onde a aplicação roda — e um deploy em UTC viraria o dia três horas antes
 * para o usuário.</p>
 */
@Configuration
public class ClockConfig {

    /** Em String porque {@code @Scheduled(zone = ...)} exige constante de compilação. */
    public static final String FUSO_ID = "America/Sao_Paulo";

    public static final ZoneId FUSO = ZoneId.of(FUSO_ID);

    @Bean
    public Clock clock() {
        return Clock.system(FUSO);
    }
}
