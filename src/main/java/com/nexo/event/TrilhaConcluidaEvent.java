package com.nexo.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um colaborador concluiu uma trilha. Publicado por
 * {@code TrilhaAtribuicaoService}; quem se interessa (ex.: o PDI, que conclui a
 * ação ligada a essa trilha) escuta com {@code @EventListener}, sem que o
 * módulo de trilhas precise conhecer o PDI.
 *
 * @param tenantId    tenant da atribuição
 * @param trilhaId    trilha concluída
 * @param usuarioId   colaborador que concluiu
 * @param concluidoEm momento da conclusão gravado na atribuição
 */
public record TrilhaConcluidaEvent(UUID tenantId, UUID trilhaId, UUID usuarioId, OffsetDateTime concluidoEm) {
}
