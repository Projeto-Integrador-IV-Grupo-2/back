package com.nexo.service.indexacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexo.entity.Arquivo;
import com.nexo.entity.Conteudo;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoArquivo;
import com.nexo.entity.Usuario;
import com.nexo.repository.ArquivoRepository;
import com.nexo.service.armazenamento.ArmazenamentoDeArquivos;
import com.nexo.service.documento.ArquivosDeTeste;
import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Fonte de texto dos arquivos com os extratores reais (PDFBox, POI e texto puro). */
@ExtendWith(MockitoExtension.class)
class ArquivosDoConteudoTest {

    @Mock
    private ArquivoRepository arquivoRepository;

    @Mock
    private ArmazenamentoDeArquivos armazenamento;

    private ArquivosDoConteudo fonte;
    private Conteudo documento;
    private Usuario autor;

    @BeforeEach
    void setUp() {
        fonte = new ArquivosDoConteudo(arquivoRepository, armazenamento, new ExtratorDeTextoFactory(
                List.of(new ExtratorPdf(), new ExtratorDocx(), new ExtratorTextoPlano())));
        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        autor = new Usuario();
        autor.setTenant(tenant);
        autor.setPapel(Perfil.ADMIN);
        documento = Conteudo.novoDocumento("Manual", autor);
        ReflectionTestUtils.setField(documento, "id", UUID.randomUUID());
    }

    @Test
    void juntaOTextoDeCadaArquivoNaOrdemDeEnvio() throws Exception {
        Arquivo pdf = Arquivo.novo(documento, "a.pdf", TipoArquivo.PDF, 1, "t/pdf", autor);
        Arquivo docx = Arquivo.novo(documento, "b.docx", TipoArquivo.DOCX, 1, "t/docx", autor);
        Arquivo md = Arquivo.novo(documento, "c.md", TipoArquivo.MD, 1, "t/md", autor);
        when(arquivoRepository.findByTenantIdAndConteudoIdOrderByCriadoEmAsc(
                documento.getTenant().getId(), documento.getId())).thenReturn(List.of(pdf, docx, md));
        when(armazenamento.abrir("t/pdf")).thenReturn(new ByteArrayInputStream(ArquivosDeTeste.pdf("Ferias anuais")));
        when(armazenamento.abrir("t/docx")).thenReturn(new ByteArrayInputStream(ArquivosDeTeste.docx("Plano de saude")));
        when(armazenamento.abrir("t/md")).thenReturn(new ByteArrayInputStream(ArquivosDeTeste.texto("# Reembolso")));

        String texto = fonte.textoDe(documento);

        assertThat(texto).contains("Ferias anuais", "Plano de saude", "# Reembolso");
        assertThat(texto.indexOf("Ferias")).isLessThan(texto.indexOf("Plano"));
        assertThat(texto.indexOf("Plano")).isLessThan(texto.indexOf("Reembolso"));
    }

    @Test
    void conteudoSemArquivoNaoTemTexto() {
        when(arquivoRepository.findByTenantIdAndConteudoIdOrderByCriadoEmAsc(
                documento.getTenant().getId(), documento.getId())).thenReturn(List.of());

        assertThat(fonte.textoDe(documento)).isEmpty();
    }

    @Test
    void arquivoAusenteFalhaParaAIndexacaoRegistrar() throws Exception {
        Arquivo pdf = Arquivo.novo(documento, "a.pdf", TipoArquivo.PDF, 1, "t/sumiu", autor);
        when(arquivoRepository.findByTenantIdAndConteudoIdOrderByCriadoEmAsc(
                documento.getTenant().getId(), documento.getId())).thenReturn(List.of(pdf));
        when(armazenamento.abrir("t/sumiu")).thenThrow(new NoSuchFileException("t/sumiu"));

        assertThatThrownBy(() -> fonte.textoDe(documento)).isInstanceOf(UncheckedIOException.class);
    }
}
