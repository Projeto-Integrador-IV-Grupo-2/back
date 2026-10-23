package com.nexo.config;

import com.nexo.service.armazenamento.ArmazenamentoDeArquivos;
import com.nexo.service.armazenamento.ArmazenamentoFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Um único {@link ArmazenamentoDeArquivos} na aplicação (Singleton do Spring,
 * sem estado). Qual implementação é decide a {@link ArmazenamentoFactory}.
 */
@Configuration
@EnableConfigurationProperties({ArmazenamentoProperties.class, DocumentosProperties.class})
public class ArmazenamentoConfig {

    @Bean
    public ArmazenamentoDeArquivos armazenamentoDeArquivos(ArmazenamentoProperties properties) {
        return ArmazenamentoFactory.criar(properties);
    }
}
