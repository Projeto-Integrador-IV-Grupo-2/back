-- Limite transacional de perguntas por usuário. A linha em assistente_limite_usuario
-- funciona como mutex no banco; os eventos abaixo contam também tentativas que
-- falharam no provedor, sem guardar o texto da pergunta ou da resposta.
CREATE TABLE assistente_limite_usuario (
    tenant_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    CONSTRAINT pk_assistente_limite_usuario PRIMARY KEY (tenant_id, usuario_id),
    CONSTRAINT fk_assistente_limite_usuario_usuario
        FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE assistente_consulta_limite (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    solicitada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_assistente_consulta_limite_usuario
        FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_assistente_consulta_limite_usuario_tempo
    ON assistente_consulta_limite (tenant_id, usuario_id, solicitada_em);

-- Preserva a quota de perguntas já gravadas nos últimos 60 segundos no momento
-- da migração, para não abrir uma janela de bypass durante a atualização.
INSERT INTO assistente_limite_usuario (tenant_id, usuario_id)
SELECT DISTINCT s.tenant_id, s.usuario_id
FROM chat_mensagem m
JOIN chat_sessao s ON s.id = m.sessao_id AND s.tenant_id = m.tenant_id
WHERE m.papel = 'USUARIO'::papel_mensagem
  AND m.criado_em > now() - INTERVAL '60 seconds'
ON CONFLICT (tenant_id, usuario_id) DO NOTHING;

INSERT INTO assistente_consulta_limite (tenant_id, usuario_id, solicitada_em)
SELECT s.tenant_id, s.usuario_id, m.criado_em
FROM chat_mensagem m
JOIN chat_sessao s ON s.id = m.sessao_id AND s.tenant_id = m.tenant_id
WHERE m.papel = 'USUARIO'::papel_mensagem
  AND m.criado_em > now() - INTERVAL '60 seconds';
