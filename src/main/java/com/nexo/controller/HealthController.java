package com.nexo.controller;

import com.nexo.dto.HealthResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check público e leve da API.
 * Complementa {@code /actuator/health} com uma resposta simples de disponibilidade.
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    @Value("${spring.application.name:nexo-api}")
    private final String applicationName;

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", applicationName, Instant.now()));
    }
}
