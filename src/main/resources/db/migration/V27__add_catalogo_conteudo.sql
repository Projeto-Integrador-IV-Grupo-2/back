-- Conteúdo de trilha e documento de Conhecimento são catálogos separados: cada
-- tela lista só o seu, e etapa de trilha só aceita conteúdo de trilha.
ALTER TABLE conteudo
    ADD COLUMN catalogo VARCHAR(20) NOT NULL DEFAULT 'TRILHA';

ALTER TABLE conteudo
    ADD CONSTRAINT ck_conteudo_catalogo CHECK (catalogo IN ('TRILHA', 'CONHECIMENTO'));

-- O que já existe: documento nasceu na tela de Conhecimento; o resto, no
-- catálogo das trilhas. Documento já usado numa etapa continua de trilha.
UPDATE conteudo c
   SET catalogo = 'CONHECIMENTO'
 WHERE c.tipo = 'DOCUMENTO'
   AND NOT EXISTS (SELECT 1 FROM trilha_etapa e WHERE e.conteudo_id = c.id);

CREATE INDEX idx_conteudo_tenant_catalogo ON conteudo (tenant_id, catalogo);
