package com.nexo.service.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexo.entity.TipoArquivo;
import com.nexo.exception.CampoInvalidoException;
import org.junit.jupiter.api.Test;

class DetectorDeTipoDeArquivoTest {

    private final DetectorDeTipoDeArquivo detector = new DetectorDeTipoDeArquivo();

    @Test
    void aceitaOsQuatroFormatosQuandoOConteudoConfirma() {
        assertThat(detector.detectar("manual.pdf", ArquivosDeTeste.pdf("Manual"))).isEqualTo(TipoArquivo.PDF);
        assertThat(detector.detectar("politica.docx", ArquivosDeTeste.docx("Politica"))).isEqualTo(TipoArquivo.DOCX);
        assertThat(detector.detectar("notas.txt", ArquivosDeTeste.texto("Férias: 30 dias"))).isEqualTo(TipoArquivo.TXT);
        assertThat(detector.detectar("guia.md", ArquivosDeTeste.texto("# Guia\n\n- item"))).isEqualTo(TipoArquivo.MD);
    }

    @Test
    void extensaoNaoDiferenciaMaiusculas() {
        assertThat(detector.detectar("MANUAL.PDF", ArquivosDeTeste.pdf("x"))).isEqualTo(TipoArquivo.PDF);
    }

    @Test
    void recusaExtensaoForaDaLista() {
        assertThatThrownBy(() -> detector.detectar("programa.exe", new byte[] {0x4D, 0x5A}))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessage("Envie um arquivo PDF, DOCX, TXT ou MD")
                .extracting("campo").isEqualTo("arquivo");
        assertThatThrownBy(() -> detector.detectar("sem-extensao", ArquivosDeTeste.texto("oi")))
                .isInstanceOf(CampoInvalidoException.class);
    }

    @Test
    void recusaArquivoComExtensaoQueNaoBateComOConteudo() {
        // Texto renomeado para .pdf.
        assertThatThrownBy(() -> detector.detectar("falso.pdf", ArquivosDeTeste.texto("não sou PDF")))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessage("O conteúdo do arquivo não é um PDF válido");
        // ZIP qualquer renomeado para .docx.
        assertThatThrownBy(() -> detector.detectar("falso.docx", ArquivosDeTeste.zipQueNaoEhDocx()))
                .isInstanceOf(CampoInvalidoException.class);
        // PDF renomeado para .txt: binário não passa como texto.
        assertThatThrownBy(() -> detector.detectar("falso.txt", new byte[] {'o', 'i', 0, 1, 2}))
                .isInstanceOf(CampoInvalidoException.class);
        // Bytes que não são UTF-8 válido.
        assertThatThrownBy(() -> detector.detectar("falso.md", new byte[] {(byte) 0xC3, (byte) 0x28}))
                .isInstanceOf(CampoInvalidoException.class);
    }
}
