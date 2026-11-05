-- =========================================================================
-- V24 — Recuperação de senha (issue #76).
--
-- token_redefinicao_senha: o link "esqueci minha senha". Guarda só o HASH
-- (SHA-256) do token; o valor só existe no e-mail enviado. Vale por
-- SENHA_TOKEN_VALIDADE e uma única vez (usado_em). Pedir um link novo apaga
-- os anteriores ainda não usados do mesmo usuário.
--
-- pedido_redefinicao_senha: conta os pedidos por e-mail e por IP, para o
-- limite (ex.: 3 a cada 15 minutos). As chaves também são hash: a tabela não
-- guarda e-mail nem IP em claro, e um e-mail sem conta conta igual a um com
-- conta (a resposta não revela quem tem cadastro).
--
-- historico_interacao ganha SENHA_REDEFINIDA. Número V24: a V23 está no
-- PR #74 (limite de perguntas do assistente).
-- =========================================================================
CREATE TABLE token_redefinicao_senha (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant (id),
    usuario_id  UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expira_em   TIMESTAMPTZ  NOT NULL,
    usado_em    TIMESTAMPTZ,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_token_redefinicao_hash UNIQUE (token_hash),
    CONSTRAINT ck_token_redefinicao_validade CHECK (expira_em > criado_em),
    -- Chave composta (V14): o token nunca aponta para usuário de outro tenant.
    CONSTRAINT fk_token_redefinicao_usuario_tenant
        FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_token_redefinicao_usuario ON token_redefinicao_senha (usuario_id);

CREATE TABLE pedido_redefinicao_senha (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_chave  VARCHAR(10)  NOT NULL,
    chave_hash  VARCHAR(64)  NOT NULL,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_pedido_redefinicao_tipo_chave CHECK (tipo_chave IN ('EMAIL', 'IP'))
);

-- A contagem do limite: pedidos desta chave desde o início da janela.
CREATE INDEX idx_pedido_redefinicao_chave_tempo
    ON pedido_redefinicao_senha (tipo_chave, chave_hash, criado_em);

-- O tipo é VARCHAR + CHECK desde a V16; acrescentar um valor é refazer o
-- CHECK (mesma lista da V22 + SENHA_REDEFINIDA).
ALTER TABLE historico_interacao DROP CONSTRAINT ck_historico_interacao_tipo;
ALTER TABLE historico_interacao
    ADD CONSTRAINT ck_historico_interacao_tipo
        CHECK (tipo IN ('TRILHA_ACESSADA', 'TRILHA_INICIADA', 'TRILHA_CONCLUIDA',
                        'ASSISTENTE_CONSULTADO', 'SENHA_REDEFINIDA'));
