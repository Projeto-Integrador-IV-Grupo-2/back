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

/** Schema da V19 (issue #58) contra PostgreSQL + pgvector real. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConteudoChunkSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void extensaoVectorEIndicesExistem() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                .isEqualTo(1);
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'conteudo_chunk'", String.class);
        assertThat(indices).contains("idx_conteudo_chunk_embedding", "idx_conteudo_chunk_tenant", "uq_conteudo_chunk_ordem");
    }

    @Test
    void ordemEhUnicaPorConteudoEEmbeddingTemTamanhoFixo() {
        UUID[] ids = conteudoDoSeed();

        jdbc.update("INSERT INTO conteudo_chunk (tenant_id, conteudo_id, ordem, texto, embedding) VALUES (?, ?, 1, 'a', ?::vector)",
                ids[0], ids[1], vetor(768));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO conteudo_chunk (tenant_id, conteudo_id, ordem, texto, embedding) VALUES (?, ?, 1, 'b', ?::vector)",
                ids[0], ids[1], vetor(768)));
    }

    @Test
    void embeddingComOutraDimensaoEhRecusado() {
        UUID[] ids = conteudoDoSeed();

        assertThrows(Exception.class, () -> jdbc.update(
                "INSERT INTO conteudo_chunk (tenant_id, conteudo_id, ordem, texto, embedding) VALUES (?, ?, 1, 'a', ?::vector)",
                ids[0], ids[1], vetor(3)));
    }

    @Test
    void apagarOConteudoApagaOsTrechos() {
        UUID[] ids = conteudoDoSeed();
        jdbc.update("INSERT INTO conteudo_chunk (tenant_id, conteudo_id, ordem, texto, embedding) VALUES (?, ?, 1, 'a', ?::vector)",
                ids[0], ids[1], vetor(768));

        jdbc.update("DELETE FROM conteudo WHERE id = ?", ids[1]);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM conteudo_chunk WHERE conteudo_id = ?", Integer.class, ids[1]))
                .isZero();
    }

    @Test
    void trechoNaoApontaParaConteudoDeOutroTenant() {
        UUID[] ids = conteudoDoSeed();
        UUID outroTenant = jdbc.queryForObject(
                "INSERT INTO tenant (nome, subdominio) VALUES ('Outra', 'outra-chunk') RETURNING id", UUID.class);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO conteudo_chunk (tenant_id, conteudo_id, ordem, texto, embedding) VALUES (?, ?, 1, 'a', ?::vector)",
                outroTenant, ids[1], vetor(768)));
    }

    /** Cria um conteúdo de texto no tenant do seed; devolve {tenantId, conteudoId}. */
    private UUID[] conteudoDoSeed() {
        UUID tenantId = jdbc.queryForObject("SELECT id FROM tenant WHERE subdominio = 'nexo'", UUID.class);
        UUID adminId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@nexo.com'", UUID.class);
        UUID conteudoId = jdbc.queryForObject(
                "INSERT INTO conteudo (tenant_id, titulo, tipo, corpo, criado_por) VALUES (?, 'T', 'TEXTO', 'c', ?) RETURNING id",
                UUID.class, tenantId, adminId);
        return new UUID[] {tenantId, conteudoId};
    }

    private static String vetor(int dimensoes) {
        StringBuilder texto = new StringBuilder("[");
        for (int i = 0; i < dimensoes; i++) {
            texto.append(i == 0 ? "" : ",").append("0.1");
        }
        return texto.append(']').toString();
    }
}
