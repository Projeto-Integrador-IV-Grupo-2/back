package com.nexo.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schema dos documentos da tela de Conhecimento (V21, issue #54) contra um
 * PostgreSQL real: colunas, chaves estrangeiras, unicidade e o tipo
 * DOCUMENTO. Se o mapeamento das entidades não batesse com o schema, o
 * contexto nem subiria ({@code ddl-auto: validate}).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentoSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void tabelaArquivoTemAsColunasEsperadas() {
        Map<String, String> colunas = new java.util.HashMap<>();
        jdbc.query("SELECT column_name, udt_name, is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'arquivo'",
                rs -> {
                    colunas.put(rs.getString("column_name"),
                            rs.getString("udt_name") + ":" + rs.getString("is_nullable"));
                });

        assertThat(colunas).containsOnly(
                Map.entry("id", "uuid:NO"),
                Map.entry("tenant_id", "uuid:NO"),
                Map.entry("conteudo_id", "uuid:NO"),
                Map.entry("nome_original", "varchar:NO"),
                Map.entry("tipo", "tipo_arquivo:NO"),
                Map.entry("mime_type", "varchar:NO"),
                Map.entry("tamanho_bytes", "int8:NO"),
                Map.entry("chave_armazenamento", "varchar:NO"),
                Map.entry("enviado_por", "uuid:NO"),
                Map.entry("criado_em", "timestamptz:NO"));
    }

    @Test
    void enunsTemOsValoresEsperados() {
        assertThat(valoresDoEnum("tipo_conteudo")).containsExactly("VIDEO", "TEXTO", "LINK", "DOCUMENTO");
        assertThat(valoresDoEnum("status_conteudo")).containsExactly("RASCUNHO", "PUBLICADO");
        assertThat(valoresDoEnum("tipo_arquivo")).containsExactly("PDF", "DOCX", "TXT", "MD");
    }

    @Test
    void chavesEstrangeirasDoArquivoSaoCompostasComOTenant() {
        List<String> fks = jdbc.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'arquivo'::regclass AND contype = 'f'",
                String.class);

        assertThat(fks).contains("fk_arquivo_conteudo_tenant", "fk_arquivo_enviado_por_tenant");
    }

    @Test
    void tabelaArquivoArquivoDoDerNaoExiste() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'arquivo_arquivo'", Integer.class))
                .isZero();
    }

    @Test
    void indicesDaListagemExistem() {
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename IN ('conteudo', 'arquivo')", String.class);

        assertThat(indices).contains(
                "idx_conteudo_tenant_categoria",
                "idx_conteudo_tenant_area",
                "idx_conteudo_tenant_status",
                "idx_arquivo_tenant_conteudo",
                "uq_arquivo_chave_armazenamento");
    }

    @Test
    void categoriaComNomeRepetidoNoMesmoTenantEhRecusada() {
        UUID tenantId = tenantDoSeed();
        jdbc.update("INSERT INTO categoria (tenant_id, nome) VALUES (?, 'Políticas')", tenantId);

        // A unicidade não diferencia maiúsculas.
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO categoria (tenant_id, nome) VALUES (?, 'POLÍTICAS')", tenantId));
    }

    @Test
    void outroTenantPodeUsarOMesmoNomeDeCategoria() {
        jdbc.update("INSERT INTO categoria (tenant_id, nome) VALUES (?, 'Políticas')", tenantDoSeed());

        jdbc.update("INSERT INTO categoria (tenant_id, nome) VALUES (?, 'Políticas')", criaTenant("cat"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM categoria WHERE nome = 'Políticas'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void documentoNaoExigeVideoCorpoNemLinkMasOsOutrosTiposContinuamExigindo() {
        UUID tenantId = tenantDoSeed();
        UUID adminId = adminDoSeed();

        jdbc.update("INSERT INTO conteudo (tenant_id, titulo, tipo, criado_por) VALUES (?, 'Manual', 'DOCUMENTO', ?)",
                tenantId, adminId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO conteudo (tenant_id, titulo, tipo, criado_por) VALUES (?, 'Sem corpo', 'TEXTO', ?)",
                tenantId, adminId));
    }

    @Test
    void chaveDeArmazenamentoEhUnica() {
        UUID[] ids = documentoDoSeed();
        insereArquivo(ids[0], ids[1], ids[2], "chave-1");

        assertThrows(DataIntegrityViolationException.class, () -> insereArquivo(ids[0], ids[1], ids[2], "chave-1"));
    }

    @Test
    void tamanhoDoArquivoPrecisaSerPositivo() {
        UUID[] ids = documentoDoSeed();

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO arquivo (tenant_id, conteudo_id, nome_original, tipo, mime_type, tamanho_bytes, "
                        + "chave_armazenamento, enviado_por) VALUES (?, ?, 'a.pdf', 'PDF', 'application/pdf', 0, 'k', ?)",
                ids[0], ids[1], ids[2]));
    }

    @Test
    void arquivoNaoApontaParaConteudoDeOutroTenant() {
        UUID[] ids = documentoDoSeed();
        UUID outroTenant = criaTenant("arq");

        assertThrows(DataIntegrityViolationException.class, () -> insereArquivo(outroTenant, ids[1], ids[2], "chave-x"));
    }

    @Test
    void apagarOConteudoApagaOsArquivosDele() {
        UUID[] ids = documentoDoSeed();
        insereArquivo(ids[0], ids[1], ids[2], "chave-2");

        jdbc.update("DELETE FROM conteudo WHERE id = ?", ids[1]);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM arquivo WHERE conteudo_id = ?", Integer.class, ids[1]))
                .isZero();
    }

    private void insereArquivo(UUID tenantId, UUID conteudoId, UUID usuarioId, String chave) {
        jdbc.update("INSERT INTO arquivo (tenant_id, conteudo_id, nome_original, tipo, mime_type, tamanho_bytes, "
                        + "chave_armazenamento, enviado_por) VALUES (?, ?, 'manual.pdf', 'PDF', 'application/pdf', 10, ?, ?)",
                tenantId, conteudoId, chave, usuarioId);
    }

    /** Documento no tenant do seed; devolve {tenantId, conteudoId, adminId}. */
    private UUID[] documentoDoSeed() {
        UUID tenantId = tenantDoSeed();
        UUID adminId = adminDoSeed();
        UUID conteudoId = jdbc.queryForObject(
                "INSERT INTO conteudo (tenant_id, titulo, tipo, criado_por) VALUES (?, 'Manual', 'DOCUMENTO', ?) "
                        + "RETURNING id",
                UUID.class, tenantId, adminId);
        return new UUID[] {tenantId, conteudoId, adminId};
    }

    private List<String> valoresDoEnum(String tipo) {
        return jdbc.queryForList("SELECT e.enumlabel FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid "
                + "WHERE t.typname = ? ORDER BY e.enumsortorder", String.class, tipo);
    }

    private UUID criaTenant(String sufixo) {
        return jdbc.queryForObject("INSERT INTO tenant (nome, subdominio) VALUES (?, ?) RETURNING id", UUID.class,
                "Outra " + sufixo, "outra-" + sufixo + "-" + UUID.randomUUID());
    }

    private UUID tenantDoSeed() {
        return jdbc.queryForObject("SELECT id FROM tenant WHERE subdominio = 'nexo'", UUID.class);
    }

    private UUID adminDoSeed() {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@nexo.com'", UUID.class);
    }
}
