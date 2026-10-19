package com.nexo.event;

import java.util.UUID;

/** Publicado quando o usuário inicia (ou reinicia) de fato uma trilha. */
public record TrilhaIniciadaEvent(UUID trilhaId, String trilhaNome, UUID tenantId, UUID usuarioId) {
}
