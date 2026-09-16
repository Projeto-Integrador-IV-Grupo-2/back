package com.nexo.dto;

import java.time.Instant;

/**
 * Corpo da resposta do health check público.
 *
 * @param status    estado da aplicação ("UP")
 * @param service   nome do serviço
 * @param timestamp momento da checagem
 */
public record HealthResponse(String status, String service, Instant timestamp) {
}
