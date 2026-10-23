-- =========================================================================
-- V21 — Documentos da tela de Conhecimento (issue #54).
--
-- categoria (V10) e conteudo (V11) já existem desde a sprint de trilhas e
-- são reaproveitadas: o documento é um conteudo. Esta migration cria só o
-- que faltava:
--   - o tipo DOCUMENTO em tipo_conteudo: o material dele vem dos arquivos
--     anexados, então não exige vídeo, corpo nem link;
--   - a tabela arquivo, com os anexos do conteúdo. O binário fica fora do
--     banco; aqui ficam nome original, tipo, tamanho e a chave gerada no
--     armazenamento (o nome enviado nunca vira caminho no disco);
--   - índices da listagem: tenant + categoria e tenant + área (tenant +
--     status já existe desde a V11).
--
-- A tabela arquivo_arquivo do DER está errada e não é criada.
--
-- Número V21: V19 e V20 estão nos PRs #67 e #68 (indexação e assistente).
-- =========================================================================

-- PostgreSQL 12+ aceita ADD VALUE dentro da transação da migration; o valor
-- novo só não pode ser usado nela. Por isso o CHECK abaixo é escrito sem
-- citar 'DOCUMENTO'.
ALTER TYPE tipo_conteudo ADD VALUE IF NOT EXISTS 'DOCUMENTO';

-- Mesmo contrato de antes para VIDEO, TEXTO e LINK; qualquer outro tipo
-- (hoje, DOCUMENTO) não exige campo de material.
ALTER TABLE conteudo DROP CONSTRAINT ck_conteudo_campos_do_tipo;
ALTER TABLE conteudo ADD CONSTRAINT ck_conteudo_campos_do_tipo CHECK (
    (tipo <> 'VIDEO' OR video_url IS NOT NULL)
    AND (tipo <> 'TEXTO' OR corpo IS NOT NULL)
    AND (tipo <> 'LINK' OR link_url IS NOT NULL)
);

CREATE INDEX idx_conteudo_tenant_categoria ON conteudo (tenant_id, categoria_id);
CREATE INDEX idx_conteudo_tenant_area ON conteudo (tenant_id, area_id);

CREATE TYPE tipo_arquivo AS ENUM ('PDF', 'DOCX', 'TXT', 'MD');

CREATE TABLE arquivo (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL REFERENCES tenant (id),
    conteudo_id         UUID          NOT NULL,
    nome_original       VARCHAR(255)  NOT NULL,
    tipo                tipo_arquivo  NOT NULL,
    mime_type           VARCHAR(100)  NOT NULL,
    tamanho_bytes       BIGINT        NOT NULL,
    chave_armazenamento VARCHAR(255)  NOT NULL,
    enviado_por         UUID          NOT NULL,
    criado_em           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_arquivo_tamanho_positivo CHECK (tamanho_bytes > 0),
    -- Cada arquivo tem sua própria chave no armazenamento.
    CONSTRAINT uq_arquivo_chave_armazenamento UNIQUE (chave_armazenamento),
    -- Chaves compostas (V14): o arquivo nunca aponta para conteúdo ou
    -- usuário de outro tenant. Apagar o conteúdo apaga os registros dos
    -- arquivos dele.
    CONSTRAINT fk_arquivo_conteudo_tenant
        FOREIGN KEY (tenant_id, conteudo_id) REFERENCES conteudo (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_arquivo_enviado_por_tenant
        FOREIGN KEY (tenant_id, enviado_por) REFERENCES usuario (tenant_id, id)
);

-- Arquivos de um conteúdo, na ordem de envio.
CREATE INDEX idx_arquivo_tenant_conteudo ON arquivo (tenant_id, conteudo_id, criado_em);
