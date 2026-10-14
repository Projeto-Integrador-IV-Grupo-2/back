-- =========================================================================
-- V9 — PDI dentro das trilhas (issue #46).
--
-- Cria competencia, pdi, pdi_item e pdi_acao seguindo o DER. O PDI não é um
-- módulo à parte: as ações do plano são, na prática, trilhas atribuídas ao
-- colaborador (pdi_acao.trilha_id). pdi_acao.conteudo_id fica para a próxima
-- sprint (a tabela conteudo ainda não existe).
--
-- Valores dos enums combinados com o grupo para esta entrega:
--   tipo_competencia : TECNICA, COMPORTAMENTAL
--   status_pdi       : ATIVO, CONCLUIDO, CANCELADO (o PDI nasce ATIVO)
--   tipo_acao_pdi    : TRILHA, MENTORIA, OUTRO
--   status_acao_pdi  : PENDENTE, EM_ANDAMENTO, CONCLUIDA, CANCELADA
-- =========================================================================
CREATE TYPE tipo_competencia AS ENUM (
    'TECNICA',
    'COMPORTAMENTAL'
);

CREATE TYPE status_pdi AS ENUM (
    'ATIVO',
    'CONCLUIDO',
    'CANCELADO'
);

CREATE TYPE tipo_acao_pdi AS ENUM (
    'TRILHA',
    'MENTORIA',
    'OUTRO'
);

CREATE TYPE status_acao_pdi AS ENUM (
    'PENDENTE',
    'EM_ANDAMENTO',
    'CONCLUIDA',
    'CANCELADA'
);

CREATE TABLE competencia (
    id          UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID             NOT NULL REFERENCES tenant (id),
    nome        VARCHAR(150)     NOT NULL,
    descricao   TEXT,
    tipo        tipo_competencia NOT NULL,
    ativo       BOOLEAN          NOT NULL DEFAULT TRUE,

    -- Nome único dentro do tenant.
    CONSTRAINT uq_competencia_tenant_nome UNIQUE (tenant_id, nome)
);

CREATE TABLE pdi (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenant (id),
    colaborador_id  UUID         NOT NULL REFERENCES usuario (id),
    -- Quem criou o PDI vira o gestor dele; não depende de usuario.gestor_id.
    gestor_id       UUID         NOT NULL REFERENCES usuario (id),
    ciclo           VARCHAR(20)  NOT NULL,
    inicio          DATE         NOT NULL,
    fim             DATE         NOT NULL,
    status          status_pdi   NOT NULL DEFAULT 'ATIVO',
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Um colaborador tem no máximo um PDI por ciclo.
    CONSTRAINT uq_pdi_tenant_colaborador_ciclo UNIQUE (tenant_id, colaborador_id, ciclo)
);

-- "PDIs em que sou o gestor" e "meu PDI" são as duas consultas da tela.
CREATE INDEX idx_pdi_tenant_gestor      ON pdi (tenant_id, gestor_id);
CREATE INDEX idx_pdi_tenant_colaborador ON pdi (tenant_id, colaborador_id);

CREATE TABLE pdi_item (
    id              UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID  NOT NULL REFERENCES tenant (id),
    pdi_id          UUID  NOT NULL REFERENCES pdi (id),
    competencia_id  UUID  NOT NULL REFERENCES competencia (id),
    nivel_atual     INT   NOT NULL,
    nivel_alvo      INT   NOT NULL,
    observacao      TEXT,

    -- Uma competência aparece uma vez só em cada PDI.
    CONSTRAINT uq_pdi_item_pdi_competencia UNIQUE (pdi_id, competencia_id),
    CONSTRAINT ck_pdi_item_nivel_atual CHECK (nivel_atual BETWEEN 1 AND 5),
    CONSTRAINT ck_pdi_item_nivel_alvo  CHECK (nivel_alvo BETWEEN 1 AND 5)
);

CREATE TABLE pdi_acao (
    id            UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID             NOT NULL REFERENCES tenant (id),
    pdi_item_id   UUID             NOT NULL REFERENCES pdi_item (id),
    descricao     TEXT,
    tipo          tipo_acao_pdi    NOT NULL,
    trilha_id     UUID             REFERENCES trilha (id),
    prazo         DATE,
    status        status_acao_pdi  NOT NULL DEFAULT 'PENDENTE',
    concluido_em  TIMESTAMPTZ,

    -- Ação TRILHA aponta para uma trilha; as outras (ex.: mentoria) ficam só
    -- com descrição.
    CONSTRAINT ck_pdi_acao_trilha
        CHECK ((tipo = 'TRILHA') = (trilha_id IS NOT NULL)),
    CONSTRAINT ck_pdi_acao_descricao
        CHECK (tipo = 'TRILHA' OR descricao IS NOT NULL)
);

CREATE INDEX idx_pdi_acao_item   ON pdi_acao (pdi_item_id);
-- Concluir uma trilha procura as ações ligadas a ela.
CREATE INDEX idx_pdi_acao_trilha ON pdi_acao (trilha_id);
