package com.nexo.dto.assistente;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/assistente/sessoes/{id}/mensagens}.
 *
 * <p>O campo é {@code conteudo}, como a tela manda. {@code pergunta} continua
 * sendo aceito como apelido para não quebrar quem já integrou com o nome
 * antigo.</p>
 */
public record PerguntaRequest(
        @JsonAlias("pergunta")
        @NotBlank(message = "Escreva a pergunta")
        @Size(max = PerguntaRequest.TAMANHO_MAXIMO, message = "A pergunta pode ter no máximo 2000 caracteres")
        String conteudo) {

    public static final int TAMANHO_MAXIMO = 2000;
}
