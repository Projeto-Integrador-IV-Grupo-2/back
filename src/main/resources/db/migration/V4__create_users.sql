CREATE TABLE usuario (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL REFERENCES tenant (id),
    nome_completo  VARCHAR(150)  NOT NULL,
    email          VARCHAR(255)  NOT NULL,
    senha_hash     VARCHAR(255)  NOT NULL,
    papel          papel_usuario NOT NULL DEFAULT 'COLABORADOR',
    cargo_id       UUID          REFERENCES cargo (id),
    area_id        UUID          REFERENCES area (id),
    gestor_id      UUID          REFERENCES usuario (id),
    data_entrada   DATE,
    ativo          BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- E-mail corporativo único dentro do tenant: rejeita duplicados no banco.
    CONSTRAINT uq_usuario_tenant_email UNIQUE (tenant_id, email),

    -- Garante que só entra hash BCrypt na coluna de senha (nunca texto puro).
    CONSTRAINT ck_usuario_senha_bcrypt
        CHECK (senha_hash ~ '^(\{bcrypt\})?\$2[aby]\$[0-9]{2}\$[A-Za-z0-9./]{53}$')
);

-- Índices para login e filtros -------------------------------------------------
CREATE INDEX idx_usuario_email        ON usuario (email);
CREATE INDEX idx_usuario_papel        ON usuario (papel);
CREATE INDEX idx_usuario_ativo        ON usuario (ativo);
CREATE INDEX idx_usuario_tenant_area  ON usuario (tenant_id, area_id);
CREATE INDEX idx_usuario_tenant_cargo ON usuario (tenant_id, cargo_id);