package com.nexo.dto;

import com.nexo.entity.Perfil;
import com.nexo.security.UsuarioAutenticado;
import java.util.UUID;

/**
 * Dados do usuário logado devolvidos no login e em {@code GET /api/auth/me}.
 *
 * @param id           identificador do usuário
 * @param tenantId     empresa (tenant) a que o usuário pertence
 * @param nomeCompleto nome exibido no frontend
 * @param email        e-mail usado no login
 * @param papel        perfil de acesso
 */
public record UsuarioAutenticadoResponse(
        UUID id,
        UUID tenantId,
        String nomeCompleto,
        String email,
        Perfil papel) {

    public static UsuarioAutenticadoResponse de(UsuarioAutenticado usuario) {
        return new UsuarioAutenticadoResponse(
                usuario.getId(),
                usuario.getTenantId(),
                usuario.getNomeCompleto(),
                usuario.getEmail(),
                usuario.getPapel());
    }
}
