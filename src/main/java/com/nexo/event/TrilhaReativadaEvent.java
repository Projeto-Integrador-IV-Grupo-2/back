package com.nexo.event;

import java.util.UUID;

/**
 * Uma trilha inativada voltou ao catálogo dos colaboradores. É o par de
 * {@link TrilhaInativadaEvent}.
 *
 * <p>Consumido por {@link SugestoesDeTrilhaListener}: a trilha volta a ser
 * candidata para as sugestões da IA do tenant.</p>
 *
 * @param trilhaId      trilha reativada
 * @param tenantId      tenant dela
 * @param reativadaPor  usuário que executou a reativação
 */
public record TrilhaReativadaEvent(UUID trilhaId, UUID tenantId, UUID reativadaPor) {
}
