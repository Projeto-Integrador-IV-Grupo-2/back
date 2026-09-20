package com.nexo.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Valida, contra um PostgreSQL real, que a {@code V6__create_trilha.sql}
 * produz o que o DER define.
 *
 * <p>O mapeamento da entidade {@code Trilha} é conferido pelo próprio
 * Hibernate: o perfil de teste roda com {@code ddl-auto: validate}, então uma
 * divergência entre entidade e tabela derruba o contexto antes de qualquer
 * teste rodar.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TrilhaSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void colunasSeguemODer() {
        List<String> colunas = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'trilha' ORDER BY column_name", String.class);

        assertThat(colunas).containsExactlyInAnyOrder(
                "id", "tenant_id", "nome", "descricao", "area_id",
                "objetivos", "prazo_dias", "criado_por", "ativo", "criado_em");
    }

    @Test
    void tenantENomeSaoObrigatorios() {
        List<String> obrigatorias = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'trilha' AND is_nullable = 'NO' "
                        + "ORDER BY column_name", String.class);

        assertThat(obrigatorias).contains("id", "tenant_id", "nome", "criado_por", "ativo", "criado_em");
    }

    @Test
    void areaEPrazoSaoOpcionais() {
        List<String> opcionais = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'trilha' AND is_nullable = 'YES' "
                        + "ORDER BY column_name", String.class);

        assertThat(opcionais).contains("area_id", "prazo_dias", "descricao", "objetivos");
    }

    @Test
    void nomeNaoEhUnicoNoTenant() {
        String nome = "Trilha Repetida";
        inserirTrilha(nome);

        // O DER não define UNIQUE em trilha.nome (diferente de area/cargo):
        // o segundo insert precisa passar.
        inserirTrilha(nome);

        Long quantidade = jdbc.queryForObject(
                "SELECT count(*) FROM trilha WHERE nome = ?", Long.class, nome);
        assertThat(quantidade).isEqualTo(2L);
    }

    @Test
    void prazoEmDiasNaoPodeSerZeroOuNegativo() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO trilha (tenant_id, nome, criado_por, prazo_dias) "
                        + "SELECT u.tenant_id, 'Prazo Inválido', u.id, 0 "
                        + "FROM usuario u WHERE u.email = 'admin@nexo.com'"));
    }

    @Test
    void existemIndicesDaListagem() {
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'trilha'", String.class);

        assertThat(indices).contains("idx_trilha_tenant_ativo", "idx_trilha_area");
    }

    private void inserirTrilha(String nome) {
        jdbc.update("INSERT INTO trilha (tenant_id, nome, criado_por) "
                + "SELECT u.tenant_id, ?, u.id FROM usuario u WHERE u.email = 'admin@nexo.com'", nome);
    }
}
