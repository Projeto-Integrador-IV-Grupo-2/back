package com.nexo.service.indexacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

/** Escolha do extrator pelo tipo e extração real de PDF, DOCX, TXT e MD gerados no próprio teste. */
class ExtratorDeTextoFactoryTest {

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ExtratorDeTextoFactory factory = new ExtratorDeTextoFactory(
            List.of(new ExtratorPdf(), new ExtratorDocx(), new ExtratorTextoPlano()));

    @Test
    void extraiTextoDePdf() throws IOException {
        String texto = factory.para("application/pdf").extrair(new ByteArrayInputStream(pdfCom("Politica de ferias")));

        assertThat(texto).contains("Politica de ferias");
    }

    @Test
    void extraiTextoDeDocx() throws IOException {
        String texto = factory.para(DOCX).extrair(new ByteArrayInputStream(docxCom("Código de conduta")));

        assertThat(texto).contains("Código de conduta");
    }

    @Test
    void extraiTxtEMarkdownEmUtf8SemBom() throws IOException {
        byte[] txt = "﻿Ação e reação".getBytes(StandardCharsets.UTF_8);
        byte[] md = "# Título\n\n- item".getBytes(StandardCharsets.UTF_8);

        assertThat(factory.para("text/plain; charset=UTF-8").extrair(new ByteArrayInputStream(txt)))
                .isEqualTo("Ação e reação");
        assertThat(factory.para("TEXT/MARKDOWN").extrair(new ByteArrayInputStream(md))).isEqualTo("# Título\n\n- item");
    }

    @Test
    void tipoSemExtratorEhRecusado() {
        assertThatThrownBy(() -> factory.para("image/png")).isInstanceOf(FormatoNaoSuportadoException.class);
        assertThatThrownBy(() -> factory.para(null)).isInstanceOf(FormatoNaoSuportadoException.class);
    }

    @Test
    void arquivoCorrompidoLancaErroDeLeitura() {
        byte[] lixo = "não sou um pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> factory.para("application/pdf").extrair(new ByteArrayInputStream(lixo)))
                .isInstanceOf(IOException.class);
    }

    @Test
    void doisExtratoresParaOMesmoTipoSaoRecusados() {
        assertThatThrownBy(() -> new ExtratorDeTextoFactory(List.of(new ExtratorPdf(), new ExtratorPdf())))
                .isInstanceOf(IllegalStateException.class);
    }

    private static byte[] pdfCom(String texto) throws IOException {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                conteudo.beginText();
                conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                conteudo.newLineAtOffset(72, 700);
                conteudo.showText(texto);
                conteudo.endText();
            }
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private static byte[] docxCom(String texto) throws IOException {
        try (XWPFDocument documento = new XWPFDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            documento.createParagraph().createRun().setText(texto);
            documento.write(saida);
            return saida.toByteArray();
        }
    }
}
