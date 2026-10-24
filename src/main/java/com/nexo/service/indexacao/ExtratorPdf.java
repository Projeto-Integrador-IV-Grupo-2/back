package com.nexo.service.indexacao;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Texto de PDF pelo PDFBox. PDF só com imagem (escaneado) devolve texto vazio. */
@Component
public class ExtratorPdf implements ExtratorDeTexto {

    @Override
    public Set<String> tiposSuportados() {
        return Set.of("application/pdf");
    }

    @Override
    public String extrair(InputStream arquivo) throws IOException {
        try (PDDocument documento = Loader.loadPDF(new RandomAccessReadBuffer(arquivo))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(documento);
        }
    }
}
