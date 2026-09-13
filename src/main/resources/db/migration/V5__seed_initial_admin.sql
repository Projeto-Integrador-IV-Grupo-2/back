INSERT INTO tenant (nome, subdominio)
VALUES ('Nexo', 'nexo')
ON CONFLICT (subdominio) DO NOTHING;

INSERT INTO usuario (tenant_id, nome_completo, email, senha_hash, papel, data_entrada, ativo)
SELECT
    t.id,
    'Administrador',
    'admin@nexo.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'ADMIN',
    CURRENT_DATE,
    TRUE
FROM tenant t
WHERE t.subdominio = 'nexo'
ON CONFLICT (tenant_id, email) DO NOTHING;
