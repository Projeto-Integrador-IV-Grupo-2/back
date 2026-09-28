-- =========================================================================
-- V8 — Amplia Área para o cadastro completo (Card 01).
--
-- A tabela `area` foi criada em V2 só com o mínimo para suportar
-- Usuario.area (id, tenant_id, nome, ativo). Esta migration adiciona os
-- campos que faltam para um cadastro completo de Área, sem tocar em V2
-- (migration já aplicada):
--   - descricao: texto livre, opcional;
--   - criado_em/atualizado_em: auditoria, no mesmo padrão de `usuario`.
--
-- DEFAULT now() em ambas as colunas de auditoria cobre também linhas que já
-- existissem antes desta migration (nenhuma existe hoje, já que Área só
-- passou a ser criada pela API a partir do módulo de catálogo) — mas deixa
-- a coluna seguramente NOT NULL de qualquer forma.
-- =========================================================================
ALTER TABLE area
    ADD COLUMN descricao     TEXT,
    ADD COLUMN criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now();
