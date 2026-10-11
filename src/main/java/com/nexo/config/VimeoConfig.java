package com.nexo.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Cliente HTTP do Vimeo, montado uma vez com os timeouts de {@code app.vimeo.*}. */
@Configuration
@EnableConfigurationProperties(VimeoProperties.class)
public class VimeoConfig {

    public static final String REST_CLIENT = "vimeoRestClient";

    @Bean
    @Qualifier(REST_CLIENT)
    public RestClient vimeoRestClient(RestClient.Builder builder, VimeoProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return builder.requestFactory(requestFactory).build();
    }
}
