package com.nexo.event;

import java.util.UUID;

/**
 * Uma trilha foi inativada e sai do catálogo dos colaboradores.
 *
 * <p>Publicado via {@code ApplicationEventPublisher}. Hoje nenhum listener
 * consome: o evento é o ponto de extensão para quem precisar reagir (avisar
 * quem tinha a trilha em andamento, por exemplo) sem acoplar isso ao service.</p>
 *
 * @param trilhaId    trilha inativada
 * @param tenantId    tenant dela
 * @param inativadaPor usuário que executou a inativação
 */
public record TrilhaInativadaEvent(UUID trilhaId, UUID tenantId, UUID inativadaPor) {
}
