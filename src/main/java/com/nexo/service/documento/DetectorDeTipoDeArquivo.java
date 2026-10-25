package com.nexo.service.documento;

import com.nexo.entity.TipoArquivo;
import com.nexo.exception.CampoInvalidoException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/**
 * Decide o {@link TipoArquivo} pelo <strong>conteúdo</strong> do arquivo, não
 * só pela extensão (issue #55).
 *
 * <ul>
 *   <li>PDF: começa com {@code %PDF-};</li>
 *   <li>DOCX: é um ZIP com {@code [Content_Types].xml} e {@code word/document.xml};</li>
 *   <li>TXT e MD: texto UTF-8 válido, sem bytes nulos. Como os dois são texto
 *       puro, quem separa um do outro é a extensão.</li>
 * </ul>
 *
 * <p>A extensão precisa ser uma das quatro e bater com o que o conteúdo é:
 * um executável renomeado para {@code .pdf} é recusado. Qualquer recusa vira
 * 400 com o erro no campo {@code arquivo}. Singleton do Spring e sem estado.</p>
 */
@Component
public class DetectorDeTipoDeArquivo {

    static final String CAMPO = "arquivo";

    private static final Map<String, TipoArquivo> POR_EXTENSAO = Map.of(
            "pdf", TipoArquivo.PDF,
            "docx", TipoArquivo.DOCX,
            "txt", TipoArquivo.TXT,
            "md", TipoArquivo.MD);

    private static final byte[] ASSINATURA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ASSINATURA_ZIP = {0x50, 0x4B, 0x03, 0x04};

    /**
     * @param nomeOriginal nome enviado pelo usuário (só a extensão importa)
     * @param conteudo     bytes do arquivo
     * @return o tipo confirmado pelo conteúdo
     * @throws CampoInvalidoException quando o tipo não é aceito ou não bate com o conteúdo
     */
    public TipoArquivo detectar(String nomeOriginal, byte[] conteudo) {
        TipoArquivo pelaExtensao = POR_EXTENSAO.get(extensao(nomeOriginal));
        if (pelaExtensao == null) {
            throw new CampoInvalidoException(CAMPO, "Envie um arquivo PDF, DOCX, TXT ou MD");
        }
        if (!conteudoE(pelaExtensao, conteudo)) {
            throw new CampoInvalidoException(CAMPO,
                    "O conteúdo do arquivo não é um " + pelaExtensao.name() + " válido");
        }
        return pelaExtensao;
    }

    private static boolean conteudoE(TipoArquivo tipo, byte[] conteudo) {
        return switch (tipo) {
            case PDF -> comecaCom(conteudo, ASSINATURA_PDF);
            case DOCX -> comecaCom(conteudo, ASSINATURA_ZIP) && ehDocumentoWord(conteudo);
            case TXT, MD -> ehTextoUtf8(conteudo);
        };
    }

    private static boolean ehDocumentoWord(byte[] conteudo) {
        boolean tiposDeConteudo = false;
        boolean documento = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(conteudo))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null && !(tiposDeConteudo && documento)) {
                tiposDeConteudo |= "[Content_Types].xml".equals(entrada.getName());
                documento |= "word/document.xml".equals(entrada.getName());
            }
        } catch (IOException | IllegalArgumentException ex) {
            return false;
        }
        return tiposDeConteudo && documento;
    }

    /** UTF-8 estrito e sem byte nulo — o que separa texto de binário. */
    private static boolean ehTextoUtf8(byte[] conteudo) {
        for (byte b : conteudo) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(conteudo));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private static boolean comecaCom(byte[] conteudo, byte[] assinatura) {
        if (conteudo.length < assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if (conteudo[i] != assinatura[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extensao(String nome) {
        if (nome == null) {
            return "";
        }
        int ponto = nome.lastIndexOf('.');
        return ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }
}
