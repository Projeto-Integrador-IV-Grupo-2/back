package com.nexo.dto.assistente;

import java.util.UUID;

/** Documento citado na resposta do assistente. */
public record FonteResponse(UUID conteudoId, String titulo) {
}
