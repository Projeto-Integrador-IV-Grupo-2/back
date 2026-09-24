-- =========================================================================
-- V6 — Trilha de aprendizagem.
--
-- Pré-requisito do Card 06 (progresso de trilha): a atribuição/progresso de
-- um usuário numa trilha (V7) precisa que a trilha já exista. Modelagem
-- reduzida ao necessário para essa FK — os demais campos (etapas, conteúdo)
-- pertencem a outros cards, ainda não implementados nesta base.
-- =========================================================================
CREATE TABLE trilha (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant (id),
    nome        VARCHAR(200) NOT NULL,
    descricao   TEXT,
    area_id     UUID         REFERENCES area (id),
    objetivos   TEXT,
    prazo_dias  INT,
    criado_por  UUID         NOT NULL REFERENCES usuario (id),
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Índice para listar/filtrar trilhas de uma área dentro do tenant.
CREATE INDEX idx_trilha_tenant_area ON trilha (tenant_id, area_id);
