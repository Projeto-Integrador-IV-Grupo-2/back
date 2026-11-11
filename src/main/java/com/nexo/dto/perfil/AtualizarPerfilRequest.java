package com.nexo.dto.perfil;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PUT /api/auth/me}: o próprio usuário só muda o nome.
 * E-mail, papel e área continuam com o ADMIN ({@code PUT /api/usuarios/{id}}).
 */
public record AtualizarPerfilRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres")
        String nomeCompleto) {
}
