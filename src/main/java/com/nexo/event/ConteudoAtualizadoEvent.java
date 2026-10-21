package com.nexo.event;

import java.util.UUID;

/**
 * Um conteúdo já publicado foi editado. A indexação da IA escuta para
 * reindexar o texto novo (issue #58).
 *
 * @param conteudoId conteúdo editado
 * @param tenantId   tenant dele
 */
public record ConteudoAtualizadoEvent(UUID conteudoId, UUID tenantId) {
}
