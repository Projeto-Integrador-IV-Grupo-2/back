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
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida, contra um PostgreSQL real (Testcontainers, perfil {@code test}), o
 * schema da V9 (competencia, pdi, pdi_item, pdi_acao — issue #46): enums com
 * os valores combinados e as constraints que o banco garante sozinho.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PdiSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void enumsExistemComOsValoresCombinados() {
        assertThat(valoresDoEnum("tipo_competencia")).containsExactly("TECNICA", "COMPORTAMENTAL");
        assertThat(valoresDoEnum("status_pdi")).containsExactly("ATIVO", "CONCLUIDO", "CANCELADO");
        assertThat(valoresDoEnum("tipo_acao_pdi")).containsExactly("TRILHA", "MENTORIA", "OUTRO");
        assertThat(valoresDoEnum("status_acao_pdi"))
                .containsExactly("PENDENTE", "EM_ANDAMENTO", "CONCLUIDA", "CANCELADA");
    }

    @Test
    void impedeDoisPdisDoMesmoColaboradorNoMesmoCiclo() {
        UUID tenantId = idDoTenantSeed();
        UUID adminId = idDoAdminSeed();
        criaPdi(tenantId, adminId, "2026");

        assertThrows(DataIntegrityViolationException.class, () -> criaPdi(tenantId, adminId, "2026"));
    }

    @Test
    void impedeNivelForaDe1a5() {
        UUID tenantId = idDoTenantSeed();
        UUID pdiId = criaPdi(tenantId, idDoAdminSeed(), "2026");
        UUID competenciaId = criaCompetencia(tenantId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO pdi_item (tenant_id, pdi_id, competencia_id, nivel_atual, nivel_alvo) "
                        + "VALUES (?, ?, ?, 0, 6)",
                tenantId, pdiId, competenciaId));
    }

    @Test
    void acaoDeTrilhaPrecisaDeTrilha() {
        UUID tenantId = idDoTenantSeed();
        UUID pdiId = criaPdi(tenantId, idDoAdminSeed(), "2026");
        UUID itemId = UUID.randomUUID();
        jdbc.update("INSERT INTO pdi_item (id, tenant_id, pdi_id, competencia_id, nivel_atual, nivel_alvo) "
                + "VALUES (?, ?, ?, ?, 2, 4)", itemId, tenantId, pdiId, criaCompetencia(tenantId));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO pdi_acao (tenant_id, pdi_item_id, tipo) VALUES (?, ?, 'TRILHA')",
                tenantId, itemId));
    }

    private List<String> valoresDoEnum(String tipo) {
        return jdbc.queryForList(
                "SELECT e.enumlabel FROM pg_enum e "
                        + "JOIN pg_type t ON t.oid = e.enumtypid "
                        + "WHERE t.typname = ? "
                        + "ORDER BY e.enumsortorder", String.class, tipo);
    }

    private UUID criaPdi(UUID tenantId, UUID usuarioId, String ciclo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pdi (id, tenant_id, colaborador_id, gestor_id, ciclo, inicio, fim) "
                        + "VALUES (?, ?, ?, ?, ?, DATE '2026-01-01', DATE '2026-12-31')",
                id, tenantId, usuarioId, usuarioId, ciclo);
        return id;
    }

    private UUID criaCompetencia(UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO competencia (id, tenant_id, nome, tipo) VALUES (?, ?, ?, 'TECNICA')",
                id, tenantId, "Competência " + id);
        return id;
    }

    private UUID idDoTenantSeed() {
        return jdbc.queryForObject("SELECT id FROM tenant WHERE subdominio = 'nexo'", UUID.class);
    }

    private UUID idDoAdminSeed() {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@nexo.com'", UUID.class);
    }
}
