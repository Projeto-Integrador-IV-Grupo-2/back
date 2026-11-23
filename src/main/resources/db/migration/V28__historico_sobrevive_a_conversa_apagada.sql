-- Apagar uma conversa do assistente falhava sempre que ela tinha alguma
-- pergunta: o histórico (ASSISTENTE_CONSULTADO) aponta para a sessão pela
-- chave composta (tenant_id, sessao_id) criada na V22, sem ação no DELETE.
--
-- O histórico é registro de atividade e deve continuar existindo; só perde o
-- vínculo com a conversa. "SET NULL (sessao_id)" (PostgreSQL 15+) zera apenas
-- a coluna da sessão — tenant_id é NOT NULL e continua igual.
ALTER TABLE historico_interacao DROP CONSTRAINT fk_historico_sessao_tenant;

ALTER TABLE historico_interacao
    ADD CONSTRAINT fk_historico_sessao_tenant
        FOREIGN KEY (tenant_id, sessao_id) REFERENCES chat_sessao (tenant_id, id)
        ON DELETE SET NULL (sessao_id);
