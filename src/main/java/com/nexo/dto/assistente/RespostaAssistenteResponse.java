package com.nexo.dto.assistente;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resposta do assistente.
 *
 * @param mensagemId id da resposta em {@code chat_mensagem}
 * @param fontes     documentos usados, sem repetição; vazio quando nada relevante foi encontrado
 */
public record RespostaAssistenteResponse(UUID mensagemId, String resposta, List<FonteResponse> fontes,
                                         OffsetDateTime criadoEm) {
}
