-- =========================================================================
-- V20 — Conversas com o assistente de IA (base da issue #59, usada pela #60).
--
-- chat_sessao: uma conversa de um usuário. Cada usuário só enxerga as
-- próprias sessões — inclusive o admin. O título nasce do começo da primeira
-- pergunta; ultima_interacao ordena a lista de conversas.
--
-- chat_mensagem: cada fala, em ordem de criado_em. A resposta do assistente
-- guarda em `fontes` (JSON) os documentos e trechos usados. Apagar a sessão
-- apaga as mensagens (CASCADE).
-- =========================================================================
CREATE TYPE contexto_chat AS ENUM ('CONHECIMENTO', 'TRILHAS');

CREATE TYPE papel_mensagem AS ENUM ('USUARIO', 'ASSISTENTE');

CREATE TABLE chat_sessao (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL REFERENCES tenant (id),
    usuario_id       UUID          NOT NULL,
    contexto         contexto_chat NOT NULL DEFAULT 'CONHECIMENTO',
    titulo           VARCHAR(120),
    iniciada_em      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    ultima_interacao TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Chave composta (V14): a sessão nunca pertence a usuário de outro tenant.
    CONSTRAINT fk_chat_sessao_usuario_tenant
        FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id)
);

-- Lista de conversas do usuário, da mais recente para a mais antiga.
CREATE INDEX idx_chat_sessao_usuario_recentes ON chat_sessao (usuario_id, ultima_interacao DESC);

CREATE TABLE chat_mensagem (
    id        UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    sessao_id UUID           NOT NULL REFERENCES chat_sessao (id) ON DELETE CASCADE,
    papel     papel_mensagem NOT NULL,
    conteudo  TEXT           NOT NULL,
    fontes    JSONB          NOT NULL DEFAULT '[]'::jsonb,
    criado_em TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Histórico da sessão em ordem e limite de perguntas por minuto.
CREATE INDEX idx_chat_mensagem_sessao_criado ON chat_mensagem (sessao_id, criado_em);
