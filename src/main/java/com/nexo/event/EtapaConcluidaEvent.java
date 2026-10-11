package com.nexo.event;

import java.util.UUID;

/**
 * Um usuário concluiu uma etapa de trilha. Consumido por
 * {@code RecalculoDeProgressoListener}, que recalcula o percentual da
 * atribuição e conclui a trilha quando for a última etapa.
 *
 * @param etapaId   etapa concluída
 * @param trilhaId  trilha da etapa
 * @param tenantId  tenant dela
 * @param usuarioId quem concluiu
 */
public record EtapaConcluidaEvent(UUID etapaId, UUID trilhaId, UUID tenantId, UUID usuarioId) {
}
