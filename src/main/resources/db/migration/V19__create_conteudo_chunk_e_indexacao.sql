-- =========================================================================
-- V19 — Trechos indexados para o assistente de IA (issue #58).
--
-- Decisão: o embedding fica numa coluna vector(768) da extensão pgvector.
--   - A busca por similaridade roda no próprio PostgreSQL (operador <=>,
--     distância de cosseno), já filtrando por tenant e por status do
--     conteúdo na mesma consulta — nenhum trecho de outra empresa ou de
--     rascunho sai do banco.
--   - O índice HNSW mantém a busca rápida com milhares de trechos, sem
--     carregar vetores para a memória da aplicação.
--   - Alternativa descartada: guardar float4[] e calcular o cosseno no Java,
--     que obriga a ler todos os vetores do tenant a cada pergunta.
--   - Consequência: o banco precisa da extensão vector (imagem
--     pgvector/pgvector:pg16 no docker-compose e nos testes; em produção, um
--     PostgreSQL com pgvector disponível).
--   - 768 dimensões = app.ia.dimensoes (gemini-embedding-001 com
--     outputDimensionality=768). Trocar de modelo com outro tamanho exige uma
--     nova migration e reindexação.
--
-- conteudo_chunk: o texto de um conteúdo publicado dividido em trechos, em
-- ordem, cada um com seu embedding. Sai junto com o conteúdo (CASCADE).
--
-- indexacao_conteudo: situação da indexação de cada conteúdo. Uma falha
-- (arquivo ilegível, provedor fora do ar) fica registrada aqui com o número
-- de tentativas e a hora da próxima, sem afetar a publicação.
-- =========================================================================
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE conteudo_chunk (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant (id),
    conteudo_id UUID         NOT NULL,
    ordem       INT          NOT NULL,
    texto       TEXT         NOT NULL,
    embedding   vector(768)  NOT NULL,
    indexado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_conteudo_chunk_ordem_positiva CHECK (ordem > 0),
    CONSTRAINT uq_conteudo_chunk_ordem UNIQUE (conteudo_id, ordem),
    -- Chave composta (V14): o trecho nunca aponta para conteúdo de outro tenant.
    CONSTRAINT fk_conteudo_chunk_conteudo_tenant
        FOREIGN KEY (tenant_id, conteudo_id) REFERENCES conteudo (tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_conteudo_chunk_tenant ON conteudo_chunk (tenant_id);
CREATE INDEX idx_conteudo_chunk_embedding ON conteudo_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TYPE status_indexacao AS ENUM ('PENDENTE', 'INDEXADO', 'FALHOU');

CREATE TABLE indexacao_conteudo (
    conteudo_id          UUID             PRIMARY KEY,
    tenant_id            UUID             NOT NULL REFERENCES tenant (id),
    status               status_indexacao NOT NULL DEFAULT 'PENDENTE',
    tentativas           INT              NOT NULL DEFAULT 0,
    ultimo_erro          VARCHAR(500),
    proxima_tentativa_em TIMESTAMPTZ,
    atualizado_em        TIMESTAMPTZ      NOT NULL DEFAULT now(),

    CONSTRAINT ck_indexacao_tentativas_nao_negativas CHECK (tentativas >= 0),
    CONSTRAINT fk_indexacao_conteudo_tenant
        FOREIGN KEY (tenant_id, conteudo_id) REFERENCES conteudo (tenant_id, id) ON DELETE CASCADE
);

-- Rotina de nova tentativa: falhas cuja hora já chegou.
CREATE INDEX idx_indexacao_conteudo_retentativa ON indexacao_conteudo (status, proxima_tentativa_em);
