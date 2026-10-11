package com.nexo.event;

import java.util.UUID;

/**
 * Etapas foram adicionadas ou removidas de uma trilha. Consumido por
 * {@code RecalculoDeProgressoListener}, que recalcula o percentual de quem
 * ainda não concluiu a trilha — adicionar uma etapa reduz o percentual de
 * quem está em andamento; trilha já concluída permanece concluída.
 *
 * @param trilhaId trilha alterada
 * @param tenantId tenant dela
 */
public record EtapasDaTrilhaAlteradasEvent(UUID trilhaId, UUID tenantId) {
}
