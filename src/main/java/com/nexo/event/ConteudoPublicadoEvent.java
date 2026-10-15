package com.nexo.event;

import java.util.UUID;

/**
 * Um conteúdo saiu de rascunho e ficou visível para os colaboradores.
 *
 * <p>Publicado via {@code ApplicationEventPublisher}. Hoje nenhum listener
 * consome: é o ponto de extensão para notificar quem estuda a área do
 * conteúdo, sem acoplar isso ao {@code ConteudoService}.</p>
 *
 * @param conteudoId   conteúdo publicado
 * @param tenantId     tenant dele
 * @param areaId       área do conteúdo (pode ser nula)
 * @param publicadoPor usuário que publicou
 */
public record ConteudoPublicadoEvent(UUID conteudoId, UUID tenantId, UUID areaId, UUID publicadoPor) {
}
