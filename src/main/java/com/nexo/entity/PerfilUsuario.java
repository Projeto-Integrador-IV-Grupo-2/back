package com.nexo.entity;

/**
 * Perfil de acesso do usuário administrativo.
 *
 * <p>{@code ADMINISTRADOR} tem acesso total ao módulo de gestão de usuários
 * ({@code /api/usuarios}); {@code COLABORADOR} é apenas cadastrado/gerenciado
 * por um administrador e não acessa esses endpoints (recebe HTTP 403).</p>
 */
public enum PerfilUsuario {
    ADMINISTRADOR,
    COLABORADOR
}
