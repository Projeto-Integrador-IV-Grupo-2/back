package com.nexo.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurações de CORS lidas de {@code app.cors.*}
 * (definidas em application.yml e sobrescritas por variáveis de ambiente).
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

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:5173", "http://localhost:3000");
        }
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        }
        if (allowedHeaders == null || allowedHeaders.isEmpty()) {
            allowedHeaders = List.of("*");
        }
        if (allowCredentials == null) {
            allowCredentials = Boolean.TRUE;
        }
        if (maxAge == null) {
            maxAge = 3600L;
        }
    }
}
