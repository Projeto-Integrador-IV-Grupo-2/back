-- =========================================================================
-- V12 — Etapas da trilha e progresso por etapa (issue #40).
--
-- trilha_etapa: sequência ordenada de conteúdos de uma trilha. A ordem é
-- única por trilha; a constraint é DEFERRABLE para que a reordenação
-- (PATCH /etapas/ordem) possa trocar posições dentro da mesma transação sem
-- colidir no meio do caminho — a unicidade é conferida só no commit.
--
-- etapa_progresso: uma linha por (etapa, usuário) concluída. Remover uma
-- etapa remove o progresso registrado nela (ON DELETE CASCADE).
-- =========================================================================
CREATE TABLE trilha_etapa (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant (id),
    trilha_id   UUID         NOT NULL REFERENCES trilha (id),
    conteudo_id UUID         NOT NULL REFERENCES conteudo (id),
    titulo      VARCHAR(200) NOT NULL,
    descricao   TEXT,
    ordem       INT          NOT NULL,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_trilha_etapa_ordem_positiva CHECK (ordem > 0),
    CONSTRAINT uq_trilha_etapa_ordem UNIQUE (trilha_id, ordem) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_trilha_etapa_conteudo ON trilha_etapa (conteudo_id);

CREATE TABLE etapa_progresso (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL REFERENCES tenant (id),
    etapa_id     UUID        NOT NULL REFERENCES trilha_etapa (id) ON DELETE CASCADE,
    usuario_id   UUID        NOT NULL REFERENCES usuario (id),
    concluido_em TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Concluir a mesma etapa duas vezes não gera segundo registro.
    CONSTRAINT uq_etapa_progresso_etapa_usuario UNIQUE (etapa_id, usuario_id)
);

CREATE INDEX idx_etapa_progresso_usuario ON etapa_progresso (usuario_id);
