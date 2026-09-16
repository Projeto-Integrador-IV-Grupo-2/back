package com.nexo.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurações de CORS lidas de {@code app.cors.*}
 * (definidas em application.yml e sobrescritas por variáveis de ambiente).
 *
 * <p>Os padrões abaixo são a última rede de segurança: valem apenas quando a
 * propriedade não foi informada em lugar nenhum.</p>
 *
 * @param allowedOrigins   origens do frontend autorizadas (ex.: http://localhost:5173)
 * @param allowedMethods   métodos HTTP permitidos
 * @param allowedHeaders   cabeçalhos permitidos na requisição
 * @param allowCredentials se cookies/Authorization podem ser enviados pelo browser
 * @param maxAge           tempo (segundos) de cache do preflight
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        Boolean allowCredentials,
        Long maxAge) {

    private static final List<String> DEFAULT_ORIGINS = List.of("http://localhost:5173", "http://localhost:3000");
    private static final List<String> DEFAULT_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> DEFAULT_HEADERS = List.of("*");

    public CorsProperties {
        allowedOrigins = listOrDefault(allowedOrigins, DEFAULT_ORIGINS);
        allowedMethods = listOrDefault(allowedMethods, DEFAULT_METHODS);
        allowedHeaders = listOrDefault(allowedHeaders, DEFAULT_HEADERS);
        allowCredentials = orDefault(allowCredentials, Boolean.TRUE);
        maxAge = orDefault(maxAge, 3600L);
    }

    private static List<String> listOrDefault(List<String> value, List<String> fallback) {
        return value == null || value.isEmpty() ? fallback : List.copyOf(value);
    }

    private static <T> T orDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
