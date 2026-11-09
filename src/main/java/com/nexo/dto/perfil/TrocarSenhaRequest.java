package com.nexo.dto.perfil;

import com.nexo.dto.usuario.validation.SenhaForte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PATCH /api/auth/me/senha}. A confirmação da nova senha fica
 * só na tela; o tamanho mínimo já é coberto pelo {@link SenhaForte}.
 */
public record TrocarSenhaRequest(
        @NotBlank(message = "senha atual é obrigatória")
        String senhaAtual,
        @NotBlank(message = "nova senha é obrigatória")
        @Size(max = 72, message = "senha deve ter no máximo 72 caracteres")
        @SenhaForte
        String novaSenha) {
}
