-- Histórico de interações com trilhas. Conteúdo/sessão serão adicionados
-- somente quando as tabelas correspondentes fizerem parte do schema.
CREATE TABLE historico_interacao (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant (id),
    usuario_id  UUID         NOT NULL,
    tipo        VARCHAR(40)  NOT NULL,
    detalhes    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_historico_usuario_tenant
        FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id),
    CONSTRAINT ck_historico_interacao_tipo
        CHECK (tipo IN ('TRILHA_ACESSADA', 'TRILHA_INICIADA', 'TRILHA_CONCLUIDA')),
    CONSTRAINT ck_historico_interacao_detalhes_objeto
        CHECK (jsonb_typeof(detalhes) = 'object')
);

CREATE INDEX idx_historico_tenant_criado
    ON historico_interacao (tenant_id, criado_em DESC, id DESC);
CREATE INDEX idx_historico_tenant_usuario_criado
    ON historico_interacao (tenant_id, usuario_id, criado_em DESC, id DESC);
CREATE INDEX idx_historico_tenant_tipo
    ON historico_interacao (tenant_id, tipo);
CREATE INDEX idx_historico_trilha_iniciada
    ON historico_interacao (tenant_id, (detalhes ->> 'trilhaId'))
    WHERE tipo = 'TRILHA_INICIADA';
