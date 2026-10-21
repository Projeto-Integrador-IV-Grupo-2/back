package com.nexo.ia;

import com.nexo.config.IaProperties;
import java.util.Locale;
import org.springframework.web.client.RestClient;

/**
 * Factory Method do {@link ClienteIa}: escolhe a implementação por
 * {@code app.ia.provedor}. Trocar de provedor é escrever uma nova
 * implementação e acrescentar um caso aqui — quem usa {@link ClienteIa} não muda.
 */
public final class ClienteIaFactory {

    private ClienteIaFactory() {
    }

    public static ClienteIa criar(IaProperties properties, RestClient restClient) {
        String provedor = properties.provedor().toLowerCase(Locale.ROOT);
        return switch (provedor) {
            case "gemini" -> new GeminiClienteIa(restClient, properties);
            case "fake" -> new ClienteIaFake(properties.dimensoes());
            default -> throw new IllegalStateException(
                    "app.ia.provedor desconhecido: '" + properties.provedor() + "' (use gemini ou fake)");
        };
    }
}
