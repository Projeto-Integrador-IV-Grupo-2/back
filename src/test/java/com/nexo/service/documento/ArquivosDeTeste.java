package com.nexo.service.documento;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Arquivos de verdade (PDF, DOCX, texto) montados em memória para os testes. */
public final class ArquivosDeTeste {

    private ArquivosDeTeste() {
    }

    /** PDF de uma página com o texto informado (só ASCII, pela fonte padrão). */
    public static byte[] pdf(String texto) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                conteudo.beginText();
                conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                conteudo.newLineAtOffset(50, 700);
                conteudo.showText(texto);
                conteudo.endText();
            }
            documento.save(saida);
            return saida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** DOCX mínimo: um ZIP com {@code [Content_Types].xml} e {@code word/document.xml}. */
    public static byte[] docx(String texto) {
        return zip(true, texto);
    }

    /** ZIP qualquer, sem {@code word/document.xml} — não é um DOCX. */
    public static byte[] zipQueNaoEhDocx() {
        return zip(false, "nada");
    }

    public static byte[] texto(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(boolean comDocumento, String texto) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(saida)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (comDocumento) {
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                        + "<w:body><w:p><w:r><w:t>" + texto + "</w:t></w:r></w:p></w:body></w:document>")
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return saida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
