package com.nexo.event;

import java.util.UUID;

/**
 * O próprio usuário trocou a senha pela tela de perfil
 * ({@code PATCH /api/auth/me/senha}), informando a senha atual.
 *
 * <p>É o par de {@link SenhaRedefinidaEvent}, que cobre a troca pelo link de
 * "esqueci minha senha". Os dois caminhos mudam a senha, então os dois
 * avisam: os mesmos observers mandam o e-mail de segurança e registram no
 * histórico, sem que o {@code PerfilService} conheça nenhum deles.</p>
 */
public record SenhaTrocadaEvent(UUID tenantId, UUID usuarioId) {
}
