-- O catálogo de conteúdo já existe desde V11. O histórico pode manter uma
-- referência relacional opcional quando uma interação estiver ligada diretamente
-- a um conteúdo; os eventos atuais de trilha deixam este campo nulo.
-- sessao_id permanece adiado até existir a tabela de sessões.
ALTER TABLE historico_interacao
    ADD COLUMN conteudo_id UUID;

-- Reutiliza a chave candidata criada em V14 para impedir referências cruzadas
-- entre tenants. A coluna é anulável, então os históricos existentes preservam-se.
ALTER TABLE historico_interacao
    ADD CONSTRAINT fk_historico_conteudo_tenant
        FOREIGN KEY (tenant_id, conteudo_id) REFERENCES conteudo (tenant_id, id);

CREATE INDEX idx_historico_tenant_conteudo
    ON historico_interacao (tenant_id, conteudo_id)
    WHERE conteudo_id IS NOT NULL;
