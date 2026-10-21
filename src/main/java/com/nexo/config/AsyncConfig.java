package com.nexo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Liga o {@code @Async}. Sem isto, a indexação da IA rodaria na thread da
 * requisição e o usuário esperaria o provedor para publicar um conteúdo.
 *
 * <p>Usa o executor padrão do Spring Boot ({@code applicationTaskExecutor}),
 * ajustável por {@code spring.task.execution.*}.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
