package com.nexo.config;

import com.nexo.ia.ClienteIa;
import com.nexo.ia.ClienteIaComRegistroDeUso;
import com.nexo.ia.ClienteIaFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Monta o {@link ClienteIa} uma vez (singleton do Spring), com o cliente HTTP e
 * os timeouts de {@code app.ia.*}. A escolha do provedor é da {@link ClienteIaFactory}.
 */
@Configuration
@EnableConfigurationProperties(IaProperties.class)
public class IaConfig {

    public static final String REST_CLIENT = "iaRestClient";

    @Bean
    @Qualifier(REST_CLIENT)
    public RestClient iaRestClient(RestClient.Builder builder, IaProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return builder.requestFactory(requestFactory).build();
    }

    /**
     * A fábrica escolhe o provedor; o resultado é envolvido pelo
     * {@link ClienteIaComRegistroDeUso}, que publica o uso de cada chamada.
     */
    @Bean
    public ClienteIa clienteIa(IaProperties properties, @Qualifier(REST_CLIENT) RestClient restClient,
                               ApplicationEventPublisher publisher) {
        return new ClienteIaComRegistroDeUso(ClienteIaFactory.criar(properties, restClient), publisher, properties);
    }
}
