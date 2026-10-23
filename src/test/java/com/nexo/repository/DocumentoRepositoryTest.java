package com.nexo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexo.entity.Arquivo;
import com.nexo.entity.Categoria;
import com.nexo.entity.Conteudo;
import com.nexo.entity.StatusConteudo;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoArquivo;
import com.nexo.entity.TipoConteudo;
import com.nexo.entity.Usuario;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entidades e repositories dos documentos (issue #54) contra um PostgreSQL
 * real: grava pelas entidades (enums nomeados e chaves compostas) e lê pelas
 * consultas por tenant.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentoRepositoryTest {

    @Autowired
    private ConteudoRepository conteudoRepository;

    @Autowired
    private ArquivoRepository arquivoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void documentoNasceRascunhoComOResponsavelEOsArquivosVoltamPorTenant() {
        Usuario admin = admin();
        Conteudo documento = conteudoRepository.saveAndFlush(Conteudo.novoDocumento("Manual do colaborador", admin));
        Arquivo primeiro = arquivoRepository.saveAndFlush(Arquivo.novo(
                documento, "manual.pdf", TipoArquivo.PDF, 2048, UUID.randomUUID().toString(), admin));
        Arquivo segundo = arquivoRepository.saveAndFlush(Arquivo.novo(
                documento, "anexo.md", TipoArquivo.MD, 64, UUID.randomUUID().toString(), admin));

        Conteudo lido = conteudoRepository.findByIdAndTenantId(documento.getId(), admin.getTenant().getId())
                .orElseThrow();
        assertThat(lido.getTipo()).isEqualTo(TipoConteudo.DOCUMENTO);
        assertThat(lido.getStatus()).isEqualTo(StatusConteudo.RASCUNHO);
        assertThat(lido.getCriadoPor().getId()).isEqualTo(admin.getId());

        assertThat(arquivoRepository.findByTenantIdAndConteudoIdOrderByCriadoEmAsc(
                        admin.getTenant().getId(), documento.getId()))
                .extracting(Arquivo::getNomeOriginal)
                .containsExactly("manual.pdf", "anexo.md");
        assertThat(arquivoRepository.countByTenantIdAndConteudoId(admin.getTenant().getId(), documento.getId()))
                .isEqualTo(2);
        Arquivo lidoArquivo = arquivoRepository.findByIdAndTenantId(segundo.getId(), admin.getTenant().getId())
                .orElseThrow();
        assertThat(lidoArquivo.getTipo()).isEqualTo(TipoArquivo.MD);
        assertThat(lidoArquivo.getMimeType()).isEqualTo("text/markdown");
        assertThat(lidoArquivo.getCriadoEm()).isNotNull();
        assertThat(primeiro.getTamanhoBytes()).isEqualTo(2048);
    }

    @Test
    void consultasPorTenantNaoEnxergamOutraEmpresa() {
        Usuario admin = admin();
        Conteudo documento = conteudoRepository.saveAndFlush(Conteudo.novoDocumento("Manual", admin));
        Arquivo arquivo = arquivoRepository.saveAndFlush(Arquivo.novo(
                documento, "manual.pdf", TipoArquivo.PDF, 10, UUID.randomUUID().toString(), admin));
        Categoria categoria = new Categoria();
        categoria.setTenant(admin.getTenant());
        categoria.setNome("Políticas " + UUID.randomUUID());
        categoria = categoriaRepository.saveAndFlush(categoria);
        UUID outroTenant = outroTenant().getId();

        assertThat(conteudoRepository.findByIdAndTenantId(documento.getId(), outroTenant)).isEmpty();
        assertThat(arquivoRepository.findByIdAndTenantId(arquivo.getId(), outroTenant)).isEmpty();
        assertThat(arquivoRepository.findByTenantIdAndConteudoIdOrderByCriadoEmAsc(outroTenant, documento.getId()))
                .isEmpty();
        assertThat(categoriaRepository.findByIdAndTenantId(categoria.getId(), outroTenant)).isEmpty();
        assertThat(categoriaRepository.findByIdAndTenantId(categoria.getId(), admin.getTenant().getId())).isPresent();
    }

    private Usuario admin() {
        return usuarioRepository.findByEmailIgnoreCase("admin@nexo.com").orElseThrow();
    }

    private Tenant outroTenant() {
        Tenant tenant = new Tenant();
        tenant.setNome("Outra empresa");
        tenant.setSubdominio("outra-doc-" + UUID.randomUUID());
        return tenantRepository.saveAndFlush(tenant);
    }
}
