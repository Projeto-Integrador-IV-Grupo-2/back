package com.nexo.event;

import java.util.UUID;

/**
 * O assistente respondeu uma pergunta (issue #60).
 *
 * <p>Consumido pelo {@link HistoricoInteracaoListener}, que registra a
 * consulta em {@code historico_interacao} (issue #48) sem acoplar isso ao
 * {@code ConsultaAssistenteService}. É publicado fora de transação, por isso o
 * listener usa {@code @EventListener} e não {@code AFTER_COMMIT}. Não carrega
 * o texto da pergunta nem da resposta.</p>
 *
 * @param encontrouFontes  falso quando a resposta foi o "não encontrei"
 * @param quantidadeFontes documentos distintos citados
 */
public record PerguntaRespondidaEvent(UUID tenantId, UUID usuarioId, UUID sessaoId, UUID mensagemId,
                                      boolean encontrouFontes, int quantidadeFontes) {
}
