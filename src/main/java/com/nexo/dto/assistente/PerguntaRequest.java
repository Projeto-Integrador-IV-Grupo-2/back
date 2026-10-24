package com.nexo.dto.assistente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/assistente/sessoes/{id}/mensagens}. */
public record PerguntaRequest(
        @NotBlank(message = "Escreva a pergunta")
        @Size(max = PerguntaRequest.TAMANHO_MAXIMO, message = "A pergunta pode ter no máximo 2000 caracteres")
        String pergunta) {

    public static final int TAMANHO_MAXIMO = 2000;
}
