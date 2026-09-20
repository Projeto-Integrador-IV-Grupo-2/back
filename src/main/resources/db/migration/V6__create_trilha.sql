-- Trilha de aprendizagem: o roteiro que é atribuído a um colaborador.
--
-- Segue o DER: sem UNIQUE em nome (duas trilhas do mesmo tenant podem se
-- chamar igual, diferente do que vale para area/cargo). As etapas
-- (trilha_etapa) não entram aqui porque dependem da tabela conteudo, que
-- ainda não existe no schema.
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
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Prazo é opcional, mas quando informado precisa fazer sentido.
    CONSTRAINT ck_trilha_prazo_dias_positivo CHECK (prazo_dias IS NULL OR prazo_dias > 0)
);

-- Índices da listagem por tenant (trilhas ativas) e do filtro por área.
CREATE INDEX idx_trilha_tenant_ativo ON trilha (tenant_id, ativo);
CREATE INDEX idx_trilha_area         ON trilha (area_id);
