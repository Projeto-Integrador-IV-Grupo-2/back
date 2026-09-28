package com.nexo.dto.usuario;

import com.nexo.entity.SituacaoUsuario;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do {@code PATCH /api/usuarios/{id}/situacao} — ativa ou desativa o
 * usuário sem excluí-lo fisicamente.
 */
public record AtualizarSituacaoRequest(
        @NotNull(message = "situação é obrigatória")
        SituacaoUsuario situacao) {
}
