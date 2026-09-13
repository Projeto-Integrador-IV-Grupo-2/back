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
 * Valida, contra um PostgreSQL real (Testcontainers, perfil {@code test}), que
 * as migrations Flyway V1..V6 produzem o schema esperado:
 *
 * <ul>
 *   <li>rodam em banco vazio (se falhassem, o contexto nem subia);</li>
 *   <li>o administrador inicial é criado;</li>
 *   <li>e-mail duplicado é rejeitado pelo banco;</li>
 *   <li>a coluna de senha só aceita hash BCrypt;</li>
 *   <li>existem índices para login e filtros.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class MigrationSchemaTest {

    /** Hash BCrypt válido (de "password") reutilizado nos inserts de teste. */
    private static final String BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final String BCRYPT_REGEX = "^\\$2[aby]\\$\\d{2}\\$[A-Za-z0-9./]{53}$";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void perfisDeAcessoExistemComoEnum() {
        List<String> papeis = jdbc.queryForList(
                "SELECT e.enumlabel FROM pg_enum e "
                        + "JOIN pg_type t ON t.oid = e.enumtypid "
                        + "WHERE t.typname = 'papel_usuario' "
                        + "ORDER BY e.enumsortorder", String.class);

        assertThat(papeis).contains("ADMINISTRADOR", "COLABORADOR");
    }

    @Test
    void administradorInicialFoiCriado() {
        Long qtd = jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE email = 'admin@nexo.com'", Long.class);
        assertThat(qtd).isEqualTo(1L);

        String papel = jdbc.queryForObject(
                "SELECT papel::text FROM usuario WHERE email = 'admin@nexo.com'", String.class);
        assertThat(papel).isEqualTo("ADMINISTRADOR");

        Boolean ativo = jdbc.queryForObject(
                "SELECT ativo FROM usuario WHERE email = 'admin@nexo.com'", Boolean.class);
        assertThat(ativo).isTrue();
    }

    @Test
    void senhaDoSeedEstaArmazenadaComoHashBcrypt() {
        String hash = jdbc.queryForObject(
                "SELECT senha_hash FROM usuario WHERE email = 'admin@nexo.com'", String.class);

        assertThat(hash)
                .isNotEqualTo("password")
                .matches(BCRYPT_REGEX);
    }

    @Test
    void emailDuplicadoNoMesmoTenantEhRejeitadoPeloBanco() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO usuario (tenant_id, nome_completo, email, senha_hash, papel) "
                        + "SELECT t.id, 'Duplicado', 'admin@nexo.com', '" + BCRYPT_HASH + "', 'COLABORADOR' "
                        + "FROM tenant t WHERE t.subdominio = 'nexo'"));
    }

    @Test
    void senhaEmTextoPuroEhRejeitadaPelaConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO usuario (tenant_id, nome_completo, email, senha_hash, papel) "
                        + "SELECT t.id, 'Texto Puro', 'texto.puro@nexo.com', 'senha-em-texto-puro', 'COLABORADOR' "
                        + "FROM tenant t WHERE t.subdominio = 'nexo'"));
    }

    @Test
    void existemIndicesParaLoginEFiltros() {
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'usuario'", String.class);

        assertThat(indices).contains(
                "uq_usuario_tenant_email",   // e-mail único (login + rejeita duplicado)
                "idx_usuario_email",         // login por e-mail
                "idx_usuario_papel",         // filtro por perfil
                "idx_usuario_ativo",         // filtro por situação
                "idx_usuario_tenant_area",   // filtro por departamento
                "idx_usuario_tenant_cargo"); // filtro por cargo
    }
}
