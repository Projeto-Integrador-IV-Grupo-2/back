package com.nexo.service.assistente;

import com.nexo.ia.MensagemIa;
import java.util.List;

/** Prompt pronto para o {@code ClienteIa}: instruções de sistema e a conversa. */
public record PromptMontado(String sistema, List<MensagemIa> mensagens) {
}
