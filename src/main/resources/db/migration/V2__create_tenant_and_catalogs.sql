CREATE TABLE tenant (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    nome        VARCHAR(150) NOT NULL,
    subdominio  VARCHAR(63)  NOT NULL,
    cnpj        VARCHAR(18),
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_subdominio UNIQUE (subdominio)
);

CREATE TABLE area (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL REFERENCES tenant (id),
    nome       VARCHAR(100) NOT NULL,
    ativo      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_area_tenant_nome UNIQUE (tenant_id, nome)
);

CREATE TABLE cargo (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL REFERENCES tenant (id),
    nome       VARCHAR(100) NOT NULL,
    ativo      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_cargo_tenant_nome UNIQUE (tenant_id, nome)
);
