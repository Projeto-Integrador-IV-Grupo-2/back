-- =========================================================================
-- V25 — Notificações dentro do sistema (issue #78).
--
-- Cada linha é um aviso para UM usuário: trilha atribuída, prazo chegando ou
-- vencido, trilha concluída por alguém da equipe, conteúdo novo na área e
-- trilha inativada. O link é a rota do front para onde o aviso leva.
--
-- referencia_id aponta para o que gerou o aviso (a atribuição, nos avisos de
-- prazo): é o que garante que a rotina diária não repete o mesmo aviso para a
-- mesma atribuição, mesmo rodando duas vezes no dia ou em duas instâncias.
--
-- Número V25: V23 e V24 estão nos PRs #74 e #85.
-- =========================================================================
CREATE TABLE notificacao (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL REFERENCES tenant (id),
    usuario_id     UUID          NOT NULL,
    tipo           VARCHAR(30)   NOT NULL,
    titulo         VARCHAR(150)  NOT NULL,
    mensagem       VARCHAR(500)  NOT NULL,
    link           VARCHAR(300)  NOT NULL,
    referencia_id  UUID,
    lida_em        TIMESTAMPTZ,
    criada_em      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_notificacao_tipo CHECK (tipo IN (
        'TRILHA_ATRIBUIDA', 'PRAZO_PROXIMO', 'PRAZO_VENCIDO',
        'TRILHA_CONCLUIDA', 'CONTEUDO_PUBLICADO', 'TRILHA_INATIVADA')),
    -- Chave composta (V14): a notificação nunca aponta para usuário de outro tenant.
    CONSTRAINT fk_notificacao_usuario_tenant
        FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id) ON DELETE CASCADE
);

-- A lista do usuário, das mais recentes para as mais antigas.
CREATE INDEX idx_notificacao_usuario_criada
    ON notificacao (tenant_id, usuario_id, criada_em DESC, id DESC);

-- O contador do sino: só as não lidas.
CREATE INDEX idx_notificacao_nao_lidas
    ON notificacao (tenant_id, usuario_id)
    WHERE lida_em IS NULL;

-- Um aviso de prazo por atribuição, por tipo.
CREATE UNIQUE INDEX uq_notificacao_aviso_de_prazo
    ON notificacao (usuario_id, tipo, referencia_id)
    WHERE tipo IN ('PRAZO_PROXIMO', 'PRAZO_VENCIDO');

-- A limpeza das lidas antigas.
CREATE INDEX idx_notificacao_lida_em
    ON notificacao (lida_em)
    WHERE lida_em IS NOT NULL;
