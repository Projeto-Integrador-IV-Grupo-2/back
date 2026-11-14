package com.nexo.event;

import java.util.UUID;

/**
 * Uma trilha foi inativada e sai do catálogo dos colaboradores.
 *
 * <p>Publicado pelo {@code TrilhaService}, só quando a trilha estava ativa.
 * Consumido pelo {@link SugestoesDeTrilhaListener}, que descarta as sugestões
 * guardadas do tenant para a trilha não continuar sendo sugerida, e pelo
 * {@code NotificacoesListener}, que avisa quem tinha a trilha. Como só é
 * publicado na mudança, inativar de novo não repete o aviso. O par dele é o
 * {@link TrilhaReativadaEvent}.</p>
 *
 * @param trilhaId    trilha inativada
 * @param tenantId    tenant dela
 * @param inativadaPor usuário que executou a inativação
 */
public record TrilhaInativadaEvent(UUID trilhaId, UUID tenantId, UUID inativadaPor) {
}
