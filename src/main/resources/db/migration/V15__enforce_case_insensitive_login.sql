-- O serviço consulta e-mails/subdomínios sem diferenciar caixa. O banco aplica
-- a mesma invariável para evitar duplicatas e corridas entre requisições.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM usuario
        GROUP BY tenant_id, upper(email)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate emails ignoring case exist within a tenant; repair rows before applying V15';
    END IF;

    IF EXISTS (
        SELECT 1 FROM tenant
        GROUP BY lower(subdominio)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate tenant subdomains ignoring case exist; repair rows before applying V15';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_usuario_tenant_email_ci ON usuario (tenant_id, upper(email));
CREATE INDEX idx_usuario_email_ci ON usuario (upper(email));
CREATE UNIQUE INDEX uq_tenant_subdominio_ci ON tenant (lower(subdominio));
