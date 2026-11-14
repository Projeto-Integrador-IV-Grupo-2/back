package com.nexo.event;

import java.util.UUID;

/**
 * Um conteúdo saiu de rascunho e ficou visível para os colaboradores.
 *
 * <p>Publicado pelo {@code ConteudoService}. Consumido pelo
 * {@code IndexadorDeConteudoListener}, que indexa o conteúdo para o assistente
 * de IA depois do commit, e pelo {@code NotificacoesListener}, que avisa os
 * colaboradores da área. O service não conhece nenhum dos dois.</p>
 *
 * @param conteudoId   conteúdo publicado
 * @param tenantId     tenant dele
 * @param areaId       área do conteúdo (pode ser nula)
 * @param publicadoPor usuário que publicou
 */
public record ConteudoPublicadoEvent(UUID conteudoId, UUID tenantId, UUID areaId, UUID publicadoPor) {
}
