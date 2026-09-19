-- =========================================================================
-- V7 — Progresso de trilha (Card 06).
--
-- Registra, por usuário e por trilha, o andamento da atribuição: quem
-- atribuiu, prazo, status e quando foi concluída. Segue o nome usado no DER
-- do projeto (trilha_atribuicao) — o percentual de conclusão NÃO é
-- armazenado aqui: é calculado a partir do progresso por etapa
-- (etapa_progresso), que pertence a outro card (depende de trilha_etapa e
-- conteudo, ainda não implementados nesta base).
-- =========================================================================
CREATE TYPE status_atribuicao AS ENUM (
    'NAO_INICIADA',
    'EM_ANDAMENTO',
    'CONCLUIDA',
    'ATRASADA'
);

CREATE TABLE trilha_atribuicao (
    id             UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID              NOT NULL REFERENCES tenant (id),
    trilha_id      UUID              NOT NULL REFERENCES trilha (id),
    usuario_id     UUID              NOT NULL REFERENCES usuario (id),
    atribuido_por  UUID              NOT NULL REFERENCES usuario (id),
    prazo          DATE,
    status         status_atribuicao NOT NULL DEFAULT 'NAO_INICIADA',
    atribuido_em   TIMESTAMPTZ       NOT NULL DEFAULT now(),
    concluido_em   TIMESTAMPTZ,

    -- Um usuário tem no máximo uma atribuição (um progresso) por trilha.
    CONSTRAINT uq_atribuicao_trilha_usuario UNIQUE (trilha_id, usuario_id)
);

-- Índice para listar o progresso de trilhas de um usuário dentro do tenant.
CREATE INDEX idx_atribuicao_tenant_usuario ON trilha_atribuicao (tenant_id, usuario_id);
