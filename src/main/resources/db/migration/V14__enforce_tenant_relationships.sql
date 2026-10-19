-- Impede relações entre registros de empresas distintas no limite do banco.
-- A migration aborta se encontrar dados inconsistentes, em vez de escondê-los
-- ou criar restrições que não representam o estado já existente.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM usuario u JOIN area a ON a.id = u.area_id WHERE u.tenant_id <> a.tenant_id)
       OR EXISTS (SELECT 1 FROM usuario u JOIN cargo c ON c.id = u.cargo_id WHERE u.tenant_id <> c.tenant_id)
       OR EXISTS (SELECT 1 FROM usuario u JOIN usuario g ON g.id = u.gestor_id WHERE u.tenant_id <> g.tenant_id)
       OR EXISTS (SELECT 1 FROM trilha t JOIN area a ON a.id = t.area_id WHERE t.tenant_id <> a.tenant_id)
       OR EXISTS (SELECT 1 FROM trilha t JOIN usuario u ON u.id = t.criado_por WHERE t.tenant_id <> u.tenant_id)
       OR EXISTS (SELECT 1 FROM trilha_atribuicao x JOIN trilha t ON t.id = x.trilha_id WHERE x.tenant_id <> t.tenant_id)
       OR EXISTS (SELECT 1 FROM trilha_atribuicao x JOIN usuario u ON u.id = x.usuario_id WHERE x.tenant_id <> u.tenant_id)
       OR EXISTS (SELECT 1 FROM trilha_atribuicao x JOIN usuario u ON u.id = x.atribuido_por WHERE x.tenant_id <> u.tenant_id)
       OR EXISTS (SELECT 1 FROM conteudo c JOIN categoria k ON k.id = c.categoria_id WHERE c.tenant_id <> k.tenant_id)
       OR EXISTS (SELECT 1 FROM conteudo c JOIN area a ON a.id = c.area_id WHERE c.tenant_id <> a.tenant_id)
       OR EXISTS (SELECT 1 FROM conteudo c JOIN usuario u ON u.id = c.criado_por WHERE c.tenant_id <> u.tenant_id)
       OR EXISTS (SELECT 1 FROM trilha_etapa e JOIN trilha t ON t.id = e.trilha_id WHERE e.tenant_id <> t.tenant_id)
       OR EXISTS (SELECT 1 FROM trilha_etapa e JOIN conteudo c ON c.id = e.conteudo_id WHERE e.tenant_id <> c.tenant_id)
       OR EXISTS (SELECT 1 FROM etapa_progresso p JOIN trilha_etapa e ON e.id = p.etapa_id WHERE p.tenant_id <> e.tenant_id)
       OR EXISTS (SELECT 1 FROM etapa_progresso p JOIN usuario u ON u.id = p.usuario_id WHERE p.tenant_id <> u.tenant_id)
       OR EXISTS (SELECT 1 FROM pdi p JOIN usuario u ON u.id = p.colaborador_id WHERE p.tenant_id <> u.tenant_id)
       OR EXISTS (SELECT 1 FROM pdi p JOIN usuario u ON u.id = p.gestor_id WHERE p.tenant_id <> u.tenant_id)
       OR EXISTS (SELECT 1 FROM pdi_item i JOIN pdi p ON p.id = i.pdi_id WHERE i.tenant_id <> p.tenant_id)
       OR EXISTS (SELECT 1 FROM pdi_item i JOIN competencia c ON c.id = i.competencia_id WHERE i.tenant_id <> c.tenant_id)
       OR EXISTS (SELECT 1 FROM pdi_acao a JOIN pdi_item i ON i.id = a.pdi_item_id WHERE a.tenant_id <> i.tenant_id)
       OR EXISTS (SELECT 1 FROM pdi_acao a JOIN trilha t ON t.id = a.trilha_id WHERE a.tenant_id <> t.tenant_id)
    THEN
        RAISE EXCEPTION 'Cross-tenant relationships exist; repair inconsistent rows before applying V14';
    END IF;
END $$;

-- Candidate keys required for tenant-aware composite foreign keys.
ALTER TABLE area ADD CONSTRAINT uq_area_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cargo ADD CONSTRAINT uq_cargo_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE usuario ADD CONSTRAINT uq_usuario_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE trilha ADD CONSTRAINT uq_trilha_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE categoria ADD CONSTRAINT uq_categoria_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE conteudo ADD CONSTRAINT uq_conteudo_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE trilha_etapa ADD CONSTRAINT uq_trilha_etapa_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE competencia ADD CONSTRAINT uq_competencia_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE pdi ADD CONSTRAINT uq_pdi_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE pdi_item ADD CONSTRAINT uq_pdi_item_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE usuario
    ADD CONSTRAINT fk_usuario_area_tenant
        FOREIGN KEY (tenant_id, area_id) REFERENCES area (tenant_id, id),
    ADD CONSTRAINT fk_usuario_cargo_tenant
        FOREIGN KEY (tenant_id, cargo_id) REFERENCES cargo (tenant_id, id),
    ADD CONSTRAINT fk_usuario_gestor_tenant
        FOREIGN KEY (tenant_id, gestor_id) REFERENCES usuario (tenant_id, id);

ALTER TABLE trilha
    ADD CONSTRAINT fk_trilha_area_tenant
        FOREIGN KEY (tenant_id, area_id) REFERENCES area (tenant_id, id),
    ADD CONSTRAINT fk_trilha_criado_por_tenant
        FOREIGN KEY (tenant_id, criado_por) REFERENCES usuario (tenant_id, id);

ALTER TABLE trilha_atribuicao
    ADD CONSTRAINT fk_atribuicao_trilha_tenant
        FOREIGN KEY (tenant_id, trilha_id) REFERENCES trilha (tenant_id, id),
    ADD CONSTRAINT fk_atribuicao_usuario_tenant
        FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id),
    ADD CONSTRAINT fk_atribuicao_autor_tenant
        FOREIGN KEY (tenant_id, atribuido_por) REFERENCES usuario (tenant_id, id);

ALTER TABLE conteudo
    ADD CONSTRAINT fk_conteudo_categoria_tenant
        FOREIGN KEY (tenant_id, categoria_id) REFERENCES categoria (tenant_id, id),
    ADD CONSTRAINT fk_conteudo_area_tenant
        FOREIGN KEY (tenant_id, area_id) REFERENCES area (tenant_id, id),
    ADD CONSTRAINT fk_conteudo_criado_por_tenant
        FOREIGN KEY (tenant_id, criado_por) REFERENCES usuario (tenant_id, id);

ALTER TABLE trilha_etapa
    ADD CONSTRAINT fk_trilha_etapa_trilha_tenant
        FOREIGN KEY (tenant_id, trilha_id) REFERENCES trilha (tenant_id, id),
    ADD CONSTRAINT fk_trilha_etapa_conteudo_tenant
        FOREIGN KEY (tenant_id, conteudo_id) REFERENCES conteudo (tenant_id, id);

ALTER TABLE etapa_progresso
    ADD CONSTRAINT fk_etapa_progresso_etapa_tenant
        FOREIGN KEY (tenant_id, etapa_id) REFERENCES trilha_etapa (tenant_id, id),
    ADD CONSTRAINT fk_etapa_progresso_usuario_tenant
        FOREIGN KEY (tenant_id, usuario_id) REFERENCES usuario (tenant_id, id);

ALTER TABLE pdi
    ADD CONSTRAINT fk_pdi_colaborador_tenant
        FOREIGN KEY (tenant_id, colaborador_id) REFERENCES usuario (tenant_id, id),
    ADD CONSTRAINT fk_pdi_gestor_tenant
        FOREIGN KEY (tenant_id, gestor_id) REFERENCES usuario (tenant_id, id);

ALTER TABLE pdi_item
    ADD CONSTRAINT fk_pdi_item_pdi_tenant
        FOREIGN KEY (tenant_id, pdi_id) REFERENCES pdi (tenant_id, id),
    ADD CONSTRAINT fk_pdi_item_competencia_tenant
        FOREIGN KEY (tenant_id, competencia_id) REFERENCES competencia (tenant_id, id);

ALTER TABLE pdi_acao
    ADD CONSTRAINT fk_pdi_acao_item_tenant
        FOREIGN KEY (tenant_id, pdi_item_id) REFERENCES pdi_item (tenant_id, id),
    ADD CONSTRAINT fk_pdi_acao_trilha_tenant
        FOREIGN KEY (tenant_id, trilha_id) REFERENCES trilha (tenant_id, id);
