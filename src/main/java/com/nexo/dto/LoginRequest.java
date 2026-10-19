package com.nexo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Credenciais enviadas em {@code POST /api/auth/login}. */
public record LoginRequest(
        @NotBlank(message = "Informe o e-mail")
        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "E-mail deve ter no máximo 255 caracteres")
        String email,

        @NotBlank(message = "Informe a senha")
        @Size(max = 100, message = "Senha deve ter no máximo 100 caracteres")
        String senha,

        @Size(max = 63, message = "Subdomínio deve ter no máximo 63 caracteres")
        @Pattern(regexp = "(?i)^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
                message = "Subdomínio inválido")
        String tenantSubdominio) {

    /** Mantém compatibilidade com testes e clientes que usam somente email/senha. */
    public LoginRequest(String email, String senha) {
        this(email, senha, null);
    }
}
