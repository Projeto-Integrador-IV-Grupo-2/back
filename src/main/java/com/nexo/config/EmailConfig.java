package com.nexo.config;

import com.nexo.service.email.EnviadorDeEmail;
import com.nexo.service.email.EnviadorDeEmailFactory;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Um único {@link EnviadorDeEmail} na aplicação (Singleton do Spring, sem
 * estado de negócio). Qual implementação é decide a {@link EnviadorDeEmailFactory}.
 */
@Configuration
@EnableConfigurationProperties({EmailProperties.class, SenhaProperties.class})
public class EmailConfig {

    @Bean
    public EnviadorDeEmail enviadorDeEmail(EmailProperties properties,
                                           ObjectProvider<JavaMailSender> mailSender,
                                           Clock clock) {
        return EnviadorDeEmailFactory.criar(properties, mailSender.getIfAvailable(), clock);
    }
}
