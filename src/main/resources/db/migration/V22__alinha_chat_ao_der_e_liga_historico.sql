-- =========================================================================
-- V22 — Fecha o que faltava da issue #59 em relação ao DER.
--
-- A V20 criou chat_sessao e chat_mensagem, mas com três diferenças para o
-- DER: titulo menor, a coluna iniciado_em com outro nome e chat_mensagem sem
-- tenant_id. Esta migration alinha as três e liga o histórico às sessões —
-- o `sessao_id` que a V18 deixou escrito que estava "adiado até existir a
-- tabela de sessões".
--
-- Numeração: a V21 já está tomada pela branch de documentos (issue #55), que
-- ainda não entrou em dev. Usar V22 evita duas migrations com o mesmo número.
-- =========================================================================

-- --- 1. chat_sessao: título do tamanho do DER -----------------------------
-- Só alarga, então nenhum título existente se perde.
ALTER TABLE chat_sessao ALTER COLUMN titulo TYPE VARCHAR(200);

-- --- 2. chat_sessao: nome da coluna como no DER ---------------------------
ALTER TABLE chat_sessao RENAME COLUMN iniciada_em TO iniciado_em;

-- --- 3. chat_mensagem ganha tenant_id -------------------------------------
-- É a única tabela do schema sem tenant_id. Hoje o isolamento vem por
-- chat_sessao; com a coluna, a mensagem passa a carregar o próprio tenant e
-- entra no mesmo padrão de chave composta que a V14 criou para as outras.
ALTER TABLE chat_mensagem ADD COLUMN tenant_id UUID;

UPDATE chat_mensagem mensagem
   SET tenant_id = sessao.tenant_id
  FROM chat_sessao sessao
 WHERE sessao.id = mensagem.sessao_id;

ALTER TABLE chat_mensagem ALTER COLUMN tenant_id SET NOT NULL;

-- Chave candidata que as FKs compostas abaixo referenciam (padrão da V14).
ALTER TABLE chat_sessao
    ADD CONSTRAINT uq_chat_sessao_tenant_id UNIQUE (tenant_id, id);

-- A mensagem nunca pertence a sessão de outro tenant. O CASCADE mantém o
-- comportamento da V20: apagar a sessão apaga as mensagens dela.
ALTER TABLE chat_mensagem
    ADD CONSTRAINT fk_chat_mensagem_sessao_tenant
        FOREIGN KEY (tenant_id, sessao_id) REFERENCES chat_sessao (tenant_id, id)
        ON DELETE CASCADE;

CREATE INDEX idx_chat_mensagem_tenant ON chat_mensagem (tenant_id);

-- --- 4. historico_interacao aponta para a sessão --------------------------
-- O DER prevê historico_interacao.sessao_id, e a V18 registrou que a coluna
-- estava adiada até a tabela de sessões existir. Ela existe desde a V20.
ALTER TABLE historico_interacao ADD COLUMN sessao_id UUID;

ALTER TABLE historico_interacao
    ADD CONSTRAINT fk_historico_sessao_tenant
        FOREIGN KEY (tenant_id, sessao_id) REFERENCES chat_sessao (tenant_id, id);

-- O tipo é VARCHAR + CHECK desde a V16; acrescentar um valor é refazer o CHECK.
ALTER TABLE historico_interacao DROP CONSTRAINT ck_historico_interacao_tipo;
ALTER TABLE historico_interacao
    ADD CONSTRAINT ck_historico_interacao_tipo
        CHECK (tipo IN ('TRILHA_ACESSADA', 'TRILHA_INICIADA', 'TRILHA_CONCLUIDA',
                        'ASSISTENTE_CONSULTADO'));

CREATE INDEX idx_historico_tenant_sessao
    ON historico_interacao (tenant_id, sessao_id)
    WHERE sessao_id IS NOT NULL;
