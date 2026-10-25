package com.nexo.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schema das conversas do assistente contra PostgreSQL real: a V20 criou as
 * tabelas e a V22 as alinhou ao DER (título de 200, {@code iniciado_em} e
 * {@code tenant_id} em {@code chat_mensagem}).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void apagarASessaoApagaAsMensagens() {
        UUID sessaoId = criaSessao();
        insereMensagem(sessaoId, "USUARIO", "oi");
        insereMensagem(sessaoId, "ASSISTENTE", "olá");

        jdbc.update("DELETE FROM chat_sessao WHERE id = ?", sessaoId);

        assertThat(contarMensagens(sessaoId)).isZero();
    }

    @Test
    void sessaoNasceNoContextoConhecimentoEMensagemSemFontesTemListaVazia() {
        UUID sessaoId = criaSessao();
        insereMensagem(sessaoId, "USUARIO", "oi");

        assertThat(jdbc.queryForObject("SELECT contexto::text FROM chat_sessao WHERE id = ?", String.class, sessaoId))
                .isEqualTo("CONHECIMENTO");
        assertThat(jdbc.queryForObject("SELECT fontes::text FROM chat_mensagem WHERE sessao_id = ?", String.class,
                sessaoId)).isEqualTo("[]");
    }

    @Test
    void sessaoNaoPertenceAUsuarioDeOutroTenant() {
        UUID usuarioId = usuarioSeed();
        UUID outroTenant = jdbc.queryForObject(
                "INSERT INTO tenant (nome, subdominio) VALUES ('Outra', 'outra-chat') RETURNING id", UUID.class);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO chat_sessao (tenant_id, usuario_id) VALUES (?, ?)", outroTenant, usuarioId));
    }

    // ---- V22: alinhamento com o DER -------------------------------------

    @Test
    void oTituloAceitaOsDuzentosCaracteresDoDer() {
        UUID sessaoId = criaSessao();
        String titulo = "a".repeat(200);

        jdbc.update("UPDATE chat_sessao SET titulo = ? WHERE id = ?", titulo, sessaoId);

        assertThat(jdbc.queryForObject("SELECT titulo FROM chat_sessao WHERE id = ?", String.class, sessaoId))
                .hasSize(200);
    }

    @Test
    void aSessaoTemIniciadoEmComoNoDer() {
        UUID sessaoId = criaSessao();

        assertThat(jdbc.queryForObject("SELECT iniciado_em IS NOT NULL FROM chat_sessao WHERE id = ?",
                Boolean.class, sessaoId)).isTrue();
    }

    @Test
    void mensagemNaoPertenceASessaoDeOutroTenant() {
        UUID sessaoId = criaSessao();
        UUID outroTenant = jdbc.queryForObject(
                "INSERT INTO tenant (nome, subdominio) VALUES ('Outra', 'outra-msg') RETURNING id", UUID.class);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO chat_mensagem (tenant_id, sessao_id, papel, conteudo) VALUES (?, ?, 'USUARIO', 'oi')",
                outroTenant, sessaoId));
    }

    @Test
    void oHistoricoPodeApontarParaUmaSessaoDoAssistente() {
        UUID sessaoId = criaSessao();

        jdbc.update("""
                INSERT INTO historico_interacao (tenant_id, usuario_id, tipo, sessao_id)
                VALUES (?, ?, 'ASSISTENTE_CONSULTADO', ?)
                """, tenantSeed(), usuarioSeed(), sessaoId);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM historico_interacao WHERE sessao_id = ?", Integer.class, sessaoId)).isOne();
    }

    @Test
    void oHistoricoRecusaSessaoDeOutroTenant() {
        UUID outroTenant = jdbc.queryForObject(
                "INSERT INTO tenant (nome, subdominio) VALUES ('Outra', 'outra-hist') RETURNING id", UUID.class);
        UUID sessaoId = criaSessao();

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO historico_interacao (tenant_id, usuario_id, tipo, sessao_id)
                VALUES (?, ?, 'ASSISTENTE_CONSULTADO', ?)
                """, outroTenant, usuarioSeed(), sessaoId));
    }

    private void insereMensagem(UUID sessaoId, String papel, String conteudo) {
        jdbc.update("INSERT INTO chat_mensagem (tenant_id, sessao_id, papel, conteudo) VALUES (?, ?, ?::papel_mensagem, ?)",
                tenantSeed(), sessaoId, papel, conteudo);
    }

    private Integer contarMensagens(UUID sessaoId) {
        return jdbc.queryForObject("SELECT count(*) FROM chat_mensagem WHERE sessao_id = ?", Integer.class, sessaoId);
    }

    private UUID criaSessao() {
        return jdbc.queryForObject("INSERT INTO chat_sessao (tenant_id, usuario_id) VALUES (?, ?) RETURNING id",
                UUID.class, tenantSeed(), usuarioSeed());
    }

    private UUID tenantSeed() {
        return jdbc.queryForObject("SELECT id FROM tenant WHERE subdominio = 'nexo'", UUID.class);
    }

    private UUID usuarioSeed() {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@nexo.com'", UUID.class);
    }
}
