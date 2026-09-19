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
 * schema criado pelas migrations V6 (trilha) e V7 (trilha_atribuicao — Card 06):
 *
 * <ul>
 *   <li>rodam em cima do schema V1..V5 (se falhassem, o contexto nem subia);</li>
 *   <li>o enum de status de atribuição existe com os valores esperados;</li>
 *   <li>existe índice para consultar o progresso de um usuário no tenant;</li>
 *   <li>é impossível ter duas atribuições da mesma trilha para o mesmo usuário.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class TrilhaSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void statusDeAtribuicaoExisteComoEnum() {
        List<String> valores = jdbc.queryForList(
                "SELECT e.enumlabel FROM pg_enum e "
                        + "JOIN pg_type t ON t.oid = e.enumtypid "
                        + "WHERE t.typname = 'status_atribuicao' "
                        + "ORDER BY e.enumsortorder", String.class);

        assertThat(valores).containsExactly("NAO_INICIADA", "EM_ANDAMENTO", "CONCLUIDA", "ATRASADA");
    }

    @Test
    void existemIndicesParaTrilhaETrilhaAtribuicao() {
        List<String> indicesTrilha = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'trilha'", String.class);
        assertThat(indicesTrilha).contains("idx_trilha_tenant_area");

        List<String> indicesAtribuicao = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'trilha_atribuicao'", String.class);
        assertThat(indicesAtribuicao).contains("uq_atribuicao_trilha_usuario", "idx_atribuicao_tenant_usuario");
    }

    @Test
    void impedeDuasAtribuicoesDaMesmaTrilhaParaOMesmoUsuario() {
        UUID tenantId = idDoTenantSeed();
        UUID adminId = idDoAdminSeed();
        UUID trilhaId = criaTrilha(tenantId, adminId);

        jdbc.update(
                "INSERT INTO trilha_atribuicao (tenant_id, trilha_id, usuario_id, atribuido_por) "
                        + "VALUES (?, ?, ?, ?)",
                tenantId, trilhaId, adminId, adminId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO trilha_atribuicao (tenant_id, trilha_id, usuario_id, atribuido_por) "
                        + "VALUES (?, ?, ?, ?)",
                tenantId, trilhaId, adminId, adminId));
    }

    private UUID criaTrilha(UUID tenantId, UUID criadoPorId) {
        UUID trilhaId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO trilha (id, tenant_id, nome, criado_por) VALUES (?, ?, ?, ?)",
                trilhaId, tenantId, "Trilha de teste " + trilhaId, criadoPorId);
        return trilhaId;
    }

    private UUID idDoTenantSeed() {
        return jdbc.queryForObject(
                "SELECT id FROM tenant WHERE subdominio = 'nexo'", UUID.class);
    }

    private UUID idDoAdminSeed() {
        return jdbc.queryForObject(
                "SELECT id FROM usuario WHERE email = 'admin@nexo.com'", UUID.class);
    }
}
