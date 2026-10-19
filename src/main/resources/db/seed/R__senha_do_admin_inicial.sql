-- Define uma senha conhecida para o administrador criado por V5__seed_initial_admin.sql.
--
-- O hash gravado na V5 não corresponde a nenhuma senha conhecida, então ninguém
-- consegue entrar com admin@nexo.com. Como uma migration já aplicada não pode
-- ser editada, a correção vem daqui.
--
-- Este arquivo fica em db/seed, que só é carregado nos perfis dev e test
-- (ver spring.flyway.locations no application.yml): nenhuma senha pública é
-- gravada em outros ambientes. É uma migration repetível (R__) porque é
-- idempotente e não deve ocupar um número de versão da sequência principal.
--
-- Senha: admin123
UPDATE usuario AS u
SET senha_hash = '$2a$10$iHhdXeodJCFtxLavXTxRceWAfxymZQrJnAPbcLH6ymHctFfVI8W9y'
WHERE lower(u.email) = 'admin@nexo.com'
  AND u.tenant_id = (SELECT id FROM tenant WHERE lower(subdominio) = 'nexo');
