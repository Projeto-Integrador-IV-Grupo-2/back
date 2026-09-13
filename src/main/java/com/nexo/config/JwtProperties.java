package com.nexo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurações do token JWT lidas de {@code app.jwt.*}
 * (definidas em application.yml e sobrescritas por variáveis de ambiente).
 *
 * @param secret     chave usada para assinar o token (HMAC-SHA); vem de {@code JWT_SECRET}
 * @param issuer     emissor gravado e exigido na validação do token
 * @param expiration tempo de validade do token no formato ISO-8601 (ex.: {@code PT2H})
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, String issuer, Duration expiration) {

    /** Mínimo exigido pelo HMAC-SHA256: 256 bits. */
    private static final int TAMANHO_MINIMO_DA_CHAVE = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Defina a variável de ambiente JWT_SECRET com a chave de assinatura do token.");
        }
        if (secret.length() < TAMANHO_MINIMO_DA_CHAVE) {
            throw new IllegalStateException(
                    "JWT_SECRET precisa ter pelo menos " + TAMANHO_MINIMO_DA_CHAVE + " caracteres.");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "nexo-api";
        }
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            expiration = Duration.ofHours(2);
        }
    }
}
