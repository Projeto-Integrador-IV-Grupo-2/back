package com.nexo.dto;

import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import java.util.UUID;

/**
 * Dados do usuário logado devolvidos no login, em {@code GET /api/auth/me} e
 * depois de o usuário editar o próprio perfil ({@code PUT /api/auth/me}).
 *
 * @param id           identificador do usuário
 * @param tenantId     empresa (tenant) a que o usuário pertence
 * @param nomeCompleto nome exibido no frontend
 * @param email        e-mail usado no login
 * @param papel        perfil de acesso
 * @param area         área do usuário, ou {@code null} se ele não tiver uma
 * @param empresa      nome da empresa (tenant), para a tela de perfil
 */
public record MeResponse(
        UUID id,
        UUID tenantId,
        String nomeCompleto,
        String email,
        Perfil papel,
        Recurso area,
        Recurso empresa) {

    /** Monta a partir da entidade: área e tenant são lidos, então chame dentro de uma transação. */
    public static MeResponse de(Usuario usuario) {
        Tenant tenant = usuario.getTenant();
        return new MeResponse(
                usuario.getId(),
                tenant.getId(),
                usuario.getNomeCompleto(),
                usuario.getEmail(),
                usuario.getPapel(),
                recursoDe(usuario.getArea()),
                new Recurso(tenant.getId(), tenant.getNome()));
    }

    private static Recurso recursoDe(Area area) {
        return area == null ? null : new Recurso(area.getId(), area.getNome());
    }

    /** Resumo de um recurso (área ou empresa) — nunca a entidade completa. */
    public record Recurso(UUID id, String nome) {
    }
}
