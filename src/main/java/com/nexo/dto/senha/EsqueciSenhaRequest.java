package com.nexo.dto.senha;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/auth/esqueci-senha}.
 *
 * @param email            e-mail da conta
 * @param tenantSubdominio opcional, como no login: com ele, o link vai só para
 *                         a conta daquela empresa; sem ele, para cada conta
 *                         ativa com esse e-mail
 */
public record EsqueciSenhaRequest(
        @NotBlank(message = "Informe o e-mail")
        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "E-mail deve ter no máximo 255 caracteres")
        String email,

        @Size(max = 63, message = "Subdomínio deve ter no máximo 63 caracteres")
        @Pattern(regexp = "(?i)^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$", message = "Subdomínio inválido")
        String tenantSubdominio) {

    public EsqueciSenhaRequest(String email) {
        this(email, null);
    }
}
