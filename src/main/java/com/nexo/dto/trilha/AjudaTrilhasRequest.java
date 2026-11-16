package com.nexo.dto.trilha;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/trilhas/sugestoes/ajuda}: o pedido livre à IA. */
public record AjudaTrilhasRequest(
        @NotBlank(message = "Escreva o pedido")
        @Size(max = AjudaTrilhasRequest.TAMANHO_MAXIMO, message = "O pedido pode ter no máximo 2000 caracteres")
        String pergunta) {

    public static final int TAMANHO_MAXIMO = 2000;
}
