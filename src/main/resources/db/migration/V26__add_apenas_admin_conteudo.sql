-- Conteúdo restrito à gestão (ex.: tabela de salários): só ADMIN e GESTOR o
-- enxergam. Para o colaborador ele não existe — nem na listagem, nem no
-- detalhe, nem nos arquivos, nem nas respostas do assistente de IA.
ALTER TABLE conteudo
    ADD COLUMN apenas_admin BOOLEAN NOT NULL DEFAULT FALSE;
