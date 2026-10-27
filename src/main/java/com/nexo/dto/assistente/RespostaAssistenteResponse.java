package com.nexo.dto.assistente;

import java.util.List;

/**
 * Resposta de {@code POST /api/assistente/sessoes/{id}/mensagens}.
 *
 * <p>{@code fontes} oferece as citações detalhadas (documento e trecho). A lista
 * {@code resposta.fontes} continua trazendo títulos para manter compatibilidade
 * com clientes existentes que consomem o histórico de mensagens.</p>
 */
public record RespostaAssistenteResponse(SessaoResponse sessao, MensagemResponse pergunta,
                                         MensagemResponse resposta, List<FonteAssistenteResponse> fontes) {
    public RespostaAssistenteResponse {
        fontes = fontes == null ? List.of() : List.copyOf(fontes);
    }
}
