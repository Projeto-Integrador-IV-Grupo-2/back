package com.nexo.dto;

import java.time.Instant;

/**
 * Resposta do login: o token a ser enviado no cabeçalho
 * {@code Authorization: Bearer <token>} e quem é o usuário autenticado.
 *
 * @param token    token JWT de acesso
 * @param tipo     esquema de autorização ({@code Bearer})
 * @param expiraEm instante em que o token deixa de valer
 * @param usuario  dados do usuário logado
 */
public record LoginResponse(
        String token,
        String tipo,
        Instant expiraEm,
        MeResponse usuario) {

    public static LoginResponse bearer(String token, Instant expiraEm, MeResponse usuario) {
        return new LoginResponse(token, "Bearer", expiraEm, usuario);
    }
}
