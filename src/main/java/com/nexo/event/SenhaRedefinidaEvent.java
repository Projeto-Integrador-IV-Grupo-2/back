package com.nexo.event;

import java.util.UUID;

/**
 * A senha de um usuário foi redefinida pelo link de "esqueci minha senha"
 * (issue #76). Publicado pelo {@code RecuperacaoSenhaService}; os listeners
 * avisam por e-mail e registram no histórico depois do commit, sem que o
 * serviço de senha conheça nenhum dos dois.
 */
public record SenhaRedefinidaEvent(UUID tenantId, UUID usuarioId) {
}
