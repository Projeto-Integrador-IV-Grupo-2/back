package com.nexo.event;

import java.util.List;
import java.util.UUID;

/**
 * Uma trilha foi atribuída a um ou mais colaboradores.
 *
 * <p>Publicado uma vez por lote, com apenas os usuários que realmente
 * receberam a atribuição — quem já tinha a trilha não entra, porque para ele
 * nada mudou. É o gancho natural para notificação, quando existir.</p>
 *
 * @param trilhaId     trilha atribuída
 * @param tenantId     tenant dela
 * @param usuarioIds   quem passou a ter a trilha neste lote
 * @param atribuidoPor usuário que executou a atribuição
 */
public record TrilhaAtribuidaEvent(UUID trilhaId, UUID tenantId, List<UUID> usuarioIds, UUID atribuidoPor) {

    public TrilhaAtribuidaEvent {
        usuarioIds = List.copyOf(usuarioIds);
    }
}
