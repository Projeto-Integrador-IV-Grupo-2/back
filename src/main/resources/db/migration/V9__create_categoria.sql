-- =========================================================================
-- V9 — Categoria de conteúdo (issue #40).
--
-- Agrupa os conteúdos de estudo (ex.: "Onboarding", "Segurança da
-- informação"). Catálogo por tenant, com nome único sem diferenciar
-- maiúsculas dentro da empresa — mesmo contrato de `area`.
-- =========================================================================
CREATE TABLE categoria (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL REFERENCES tenant (id),
    nome          VARCHAR(100) NOT NULL,
    descricao     TEXT,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_categoria_tenant_nome ON categoria (tenant_id, lower(nome));
