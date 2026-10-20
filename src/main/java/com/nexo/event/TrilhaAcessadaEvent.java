package com.nexo.event;

import java.util.UUID;

/** Publicado após a API autorizar e resolver o detalhe da trilha no tenant. */
public record TrilhaAcessadaEvent(UUID trilhaId, String trilhaNome, UUID tenantId, UUID usuarioId) {
}
