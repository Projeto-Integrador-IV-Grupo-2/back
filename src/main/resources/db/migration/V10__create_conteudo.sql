-- =========================================================================
-- V10 — Conteúdo de estudo (issue #40).
--
-- Material usado nas etapas das trilhas. Os vídeos ficam hospedados no
-- Vimeo: aqui guardamos só o link, a duração e a miniatura, preenchidas pela
-- API a partir do oEmbed do Vimeo no momento em que o conteúdo é salvo.
--
-- Campos por tipo (garantidos pelo CHECK abaixo):
--   - VIDEO: video_url obrigatório;
--   - TEXTO: corpo obrigatório;
--   - LINK:  link_url obrigatório.
--
-- Colaborador só enxerga conteúdo PUBLICADO; RASCUNHO é visível apenas para
-- ADMIN e GESTOR.
-- =========================================================================
CREATE TYPE tipo_conteudo AS ENUM ('VIDEO', 'TEXTO', 'LINK');

CREATE TYPE status_conteudo AS ENUM ('RASCUNHO', 'PUBLICADO');

CREATE TABLE conteudo (
    id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID            NOT NULL REFERENCES tenant (id),
    categoria_id     UUID            REFERENCES categoria (id),
    area_id          UUID            REFERENCES area (id),
    titulo           VARCHAR(200)    NOT NULL,
    descricao        TEXT,
    tipo             tipo_conteudo   NOT NULL,
    status           status_conteudo NOT NULL DEFAULT 'RASCUNHO',
    video_url        VARCHAR(500),
    duracao_segundos INT,
    miniatura_url    VARCHAR(500),
    corpo            TEXT,
    link_url         VARCHAR(500),
    criado_por       UUID            NOT NULL REFERENCES usuario (id),
    criado_em        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    atualizado_em    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    publicado_em     TIMESTAMPTZ,

    CONSTRAINT ck_conteudo_campos_do_tipo CHECK (
        (tipo = 'VIDEO' AND video_url IS NOT NULL)
        OR (tipo = 'TEXTO' AND corpo IS NOT NULL)
        OR (tipo = 'LINK' AND link_url IS NOT NULL)
    ),
    CONSTRAINT ck_conteudo_duracao_positiva CHECK (duracao_segundos IS NULL OR duracao_segundos >= 0)
);

-- Listagem do catálogo com os filtros mais comuns.
CREATE INDEX idx_conteudo_tenant_status ON conteudo (tenant_id, status);
CREATE INDEX idx_conteudo_categoria ON conteudo (categoria_id);
-- Contagem de conteúdos por área (AreaResponse.totalConteudos).
CREATE INDEX idx_conteudo_area ON conteudo (area_id);
