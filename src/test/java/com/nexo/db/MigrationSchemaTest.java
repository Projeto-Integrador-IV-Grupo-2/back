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
 * Valida, contra um PostgreSQL real (Testcontainers, perfil {@code test}), que
 * as migrations Flyway produzem o schema esperado:
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

    /** Hash BCrypt bem-formado, reutilizado nos inserts de teste; a senha de origem é irrelevante aqui. */
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

        assertThat(papeis).contains("ADMIN", "COLABORADOR");
    }

    @Test
    void administradorInicialFoiCriado() {
        Long qtd = jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE email = 'admin@nexo.com'", Long.class);
        assertThat(qtd).isEqualTo(1L);

        String papel = jdbc.queryForObject(
                "SELECT papel::text FROM usuario WHERE email = 'admin@nexo.com'", String.class);
        assertThat(papel).isEqualTo("ADMIN");

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
                        + "SELECT t.id, 'Duplicado', 'ADMIN@nexo.com', '" + BCRYPT_HASH + "', 'COLABORADOR' "
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
    void historicoNaoPodeReferenciarConteudoDeOutroTenant() {
        UUID tenantPrincipal = jdbc.queryForObject(
                "SELECT tenant_id FROM usuario WHERE email = 'admin@nexo.com'", UUID.class);
        UUID adminId = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE email = 'admin@nexo.com'", UUID.class);
        String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UUID tenantOutro = null;
        UUID usuarioOutro = null;
        UUID conteudoOutro = null;
        try {
            tenantOutro = jdbc.queryForObject(
                    "INSERT INTO tenant (nome, subdominio) VALUES (?, ?) RETURNING id",
                    UUID.class, "Tenant conteúdo " + sufixo, "conteudo-" + sufixo);
            usuarioOutro = jdbc.queryForObject(
                    "INSERT INTO usuario (tenant_id, nome_completo, email, senha_hash, papel) "
                            + "VALUES (?, ?, ?, ?, 'COLABORADOR') RETURNING id",
                    UUID.class, tenantOutro, "Usuário conteúdo", "conteudo-" + sufixo + "@empresa.com",
                    BCRYPT_HASH);
            conteudoOutro = jdbc.queryForObject(
                    "INSERT INTO conteudo (tenant_id, titulo, tipo, corpo, criado_por) "
                            + "VALUES (?, ?, 'TEXTO'::tipo_conteudo, ?, ?) RETURNING id",
                    UUID.class, tenantOutro, "Conteúdo isolado", "Texto de teste", usuarioOutro);

            UUID finalConteudoOutro = conteudoOutro;
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO historico_interacao (tenant_id, usuario_id, conteudo_id, tipo, detalhes) "
                            + "VALUES (?, ?, ?, 'TRILHA_ACESSADA', '{}'::jsonb)",
                    tenantPrincipal, adminId, finalConteudoOutro));
        } finally {
            if (conteudoOutro != null) {
                // Também limpa a linha caso a constraint esteja ausente e a asserção falhe.
                jdbc.update("DELETE FROM historico_interacao WHERE tenant_id = ? AND usuario_id = ? AND conteudo_id = ?",
                        tenantPrincipal, adminId, conteudoOutro);
                jdbc.update("DELETE FROM conteudo WHERE id = ?", conteudoOutro);
            }
            if (usuarioOutro != null) {
                jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioOutro);
            }
            if (tenantOutro != null) {
                jdbc.update("DELETE FROM tenant WHERE id = ?", tenantOutro);
            }
        }
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
                "idx_usuario_tenant_cargo",  // filtro por cargo
                "uq_usuario_tenant_email_ci",
                "idx_usuario_email_ci");
    }

    @Test
    void historicoTemDetalhesJsonbEChaveCompostaDeTenant() {
        String tipoDetalhes = jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'historico_interacao' AND column_name = 'detalhes'",
                String.class);
        assertThat(tipoDetalhes).isEqualTo("jsonb");

        List<String> colunas = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'historico_interacao'", String.class);
        assertThat(colunas).contains("id", "tenant_id", "usuario_id", "conteudo_id", "tipo", "detalhes", "criado_em")
                .doesNotContain("sessao_id");

        List<String> constraints = jdbc.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'historico_interacao'::regclass",
                String.class);
        assertThat(constraints).contains("fk_historico_usuario_tenant", "fk_historico_conteudo_tenant",
                "ck_historico_interacao_tipo", "ck_historico_interacao_detalhes_objeto");
    }
}
