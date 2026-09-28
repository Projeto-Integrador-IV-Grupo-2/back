package com.nexo.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Valida, contra um PostgreSQL real (Testcontainers, perfil {@code test}), o
 * schema criado pela migration V8 (Card 01 — cadastro completo de Área):
 *
 * <ul>
 *   <li>roda em cima do schema V1..V7 (se falhasse, o contexto nem subia);</li>
 *   <li>as colunas novas ({@code descricao}, {@code criado_em},
 *       {@code atualizado_em}) existem e funcionam como esperado;</li>
 *   <li>a unicidade por {@code (tenant_id, nome)}, de V2, continua valendo —
 *       V8 só adiciona colunas, nunca deveria ter tocado nessa constraint.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class AreaSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void colunasDeAuditoriaSaoPreenchidasAutomaticamentePeloBanco() {
        UUID tenantId = idDoTenantSeed();
        String nome = "Área de teste " + UUID.randomUUID();

        jdbc.update(
                "INSERT INTO area (tenant_id, nome, descricao) VALUES (?, ?, ?)",
                tenantId, nome, "Descrição livre da área");

        var linha = jdbc.queryForMap(
                "SELECT descricao, criado_em, atualizado_em FROM area WHERE nome = ?", nome);

        assertThat(linha.get("descricao")).isEqualTo("Descrição livre da área");
        assertThat(linha.get("criado_em")).isNotNull();
        assertThat(linha.get("atualizado_em")).isNotNull();
    }

    @Test
    void descricaoEhOpcional() {
        UUID tenantId = idDoTenantSeed();
        String nome = "Área sem descrição " + UUID.randomUUID();

        // Não deve lançar: descricao é opcional (sem NOT NULL na migration).
        jdbc.update("INSERT INTO area (tenant_id, nome) VALUES (?, ?)", tenantId, nome);

        String descricao = jdbc.queryForObject(
                "SELECT descricao FROM area WHERE nome = ?", String.class, nome);
        assertThat(descricao).isNull();
    }

    @Test
    void unicidadePorTenantENomeContinuaValendoDepoisDaV8() {
        UUID tenantId = idDoTenantSeed();
        String nome = "Área duplicada " + UUID.randomUUID();

        jdbc.update("INSERT INTO area (tenant_id, nome) VALUES (?, ?)", tenantId, nome);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO area (tenant_id, nome) VALUES (?, ?)", tenantId, nome));
    }

    @Test
    void existeIndiceDeUnicidadePorTenantENome() {
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'area'", String.class);

        assertThat(indices).contains("uq_area_tenant_nome");
    }

    private UUID idDoTenantSeed() {
        return jdbc.queryForObject(
                "SELECT id FROM tenant WHERE subdominio = 'nexo'", UUID.class);
    }
}
