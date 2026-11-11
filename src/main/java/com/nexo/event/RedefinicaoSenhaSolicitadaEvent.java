package com.nexo.event;

import java.util.UUID;

/**
 * Um link de redefinição foi gerado e precisa ir por e-mail (issue #76).
 *
 * <p>Carrega o token em claro só em memória, até o listener montar o e-mail:
 * ele nunca vai para o banco nem para log. O {@link #toString()} esconde o
 * token, para ele não aparecer num log por acidente.</p>
 */
public record RedefinicaoSenhaSolicitadaEvent(
        UUID usuarioId, String email, String nome, String nomeEmpresa, String token) {

    @Override
    public String toString() {
        return "RedefinicaoSenhaSolicitadaEvent[usuarioId=" + usuarioId + "]";
    }
}
