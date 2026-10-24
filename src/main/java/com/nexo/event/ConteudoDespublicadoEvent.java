package com.nexo.event;

import java.util.UUID;

/**
 * Um conteúdo voltou para rascunho. A indexação da IA escuta para remover os
 * trechos dele (issue #58).
 *
 * @param conteudoId conteúdo despublicado
 * @param tenantId   tenant dele
 */
public record ConteudoDespublicadoEvent(UUID conteudoId, UUID tenantId) {
}
