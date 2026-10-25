package com.nexo.dto.assistente;

import com.nexo.entity.ChatSessao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PATCH /api/assistente/sessoes/{id}}. O limite acompanha a
 * coluna {@code chat_sessao.titulo}.
 */
public record RenomearSessaoRequest(
        @NotBlank(message = "Informe o novo título da conversa")
        @Size(max = ChatSessao.TAMANHO_MAXIMO_TITULO,
                message = "O título pode ter no máximo 120 caracteres")
        String titulo) {
}
