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

/** Schema da V20 (conversas do assistente) contra PostgreSQL real. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void apagarASessaoApagaAsMensagens() {
        UUID sessaoId = criaSessao();
        jdbc.update("INSERT INTO chat_mensagem (sessao_id, papel, conteudo) VALUES (?, 'USUARIO', 'oi')", sessaoId);
        jdbc.update("INSERT INTO chat_mensagem (sessao_id, papel, conteudo, fontes) VALUES (?, 'ASSISTENTE', 'olá', '[{\"titulo\":\"T\"}]'::jsonb)",
                sessaoId);

        jdbc.update("DELETE FROM chat_sessao WHERE id = ?", sessaoId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_mensagem WHERE sessao_id = ?", Integer.class, sessaoId))
                .isZero();
    }

    @Test
    void sessaoNasceNoContextoConhecimentoEMensagemSemFontesTemListaVazia() {
        UUID sessaoId = criaSessao();
        jdbc.update("INSERT INTO chat_mensagem (sessao_id, papel, conteudo) VALUES (?, 'USUARIO', 'oi')", sessaoId);

        assertThat(jdbc.queryForObject("SELECT contexto::text FROM chat_sessao WHERE id = ?", String.class, sessaoId))
                .isEqualTo("CONHECIMENTO");
        assertThat(jdbc.queryForObject("SELECT fontes::text FROM chat_mensagem WHERE sessao_id = ?", String.class, sessaoId))
                .isEqualTo("[]");
    }

    @Test
    void sessaoNaoPertenceAUsuarioDeOutroTenant() {
        UUID usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@nexo.com'", UUID.class);
        UUID outroTenant = jdbc.queryForObject(
                "INSERT INTO tenant (nome, subdominio) VALUES ('Outra', 'outra-chat') RETURNING id", UUID.class);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO chat_sessao (tenant_id, usuario_id) VALUES (?, ?)", outroTenant, usuarioId));
    }

    private UUID criaSessao() {
        UUID tenantId = jdbc.queryForObject("SELECT id FROM tenant WHERE subdominio = 'nexo'", UUID.class);
        UUID usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@nexo.com'", UUID.class);
        return jdbc.queryForObject("INSERT INTO chat_sessao (tenant_id, usuario_id) VALUES (?, ?) RETURNING id",
                UUID.class, tenantId, usuarioId);
    }
}
