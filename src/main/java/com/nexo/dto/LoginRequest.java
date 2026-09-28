package com.nexo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciais enviadas em {@code POST /api/auth/login}.
 *
 * @param email e-mail corporativo do usuário
 * @param senha senha em texto puro (conferida contra o hash BCrypt gravado)
 */
public record LoginRequest(
        @NotBlank(message = "Informe o e-mail")
        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "E-mail deve ter no máximo 255 caracteres")
        String email,

        @NotBlank(message = "Informe a senha")
        @Size(max = 100, message = "Senha deve ter no máximo 100 caracteres")
        String senha) {
}
