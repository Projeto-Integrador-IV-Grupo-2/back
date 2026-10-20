-- Marca a primeira transição real de início para que repetir o endpoint não
-- gere eventos duplicados e para distinguir uma atribuição vencida ainda não
-- iniciada de uma trilha que foi iniciada e depois ficou atrasada.
ALTER TABLE trilha_atribuicao
    ADD COLUMN iniciado_em TIMESTAMPTZ;

-- Backfill dos estados cuja semântica demonstra que o colaborador já iniciou.
-- ATRASADA permanece NULL porque o status sozinho não informa se chegou a iniciar.
UPDATE trilha_atribuicao
   SET iniciado_em = COALESCE(concluido_em, atribuido_em)
 WHERE status IN ('EM_ANDAMENTO', 'CONCLUIDA');
