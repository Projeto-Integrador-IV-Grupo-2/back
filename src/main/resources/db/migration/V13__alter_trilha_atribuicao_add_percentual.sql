-- =========================================================================
-- V13 — Percentual real de conclusão da trilha (issue #40).
--
-- Até aqui o percentual era estimado pelo status da atribuição. Com as
-- etapas (V12), ele passa a ser etapas concluídas / total de etapas, gravado
-- na atribuição e recalculado sempre que o usuário conclui uma etapa ou as
-- etapas da trilha mudam. Gravar (em vez de calcular a cada leitura) mantém
-- as listagens e o acompanhamento sem uma contagem extra por linha.
--
-- Atribuições já concluídas antes desta migration ficam com 100.
-- =========================================================================
ALTER TABLE trilha_atribuicao
    ADD COLUMN percentual INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_atribuicao_percentual CHECK (percentual BETWEEN 0 AND 100);

UPDATE trilha_atribuicao SET percentual = 100 WHERE status = 'CONCLUIDA';
