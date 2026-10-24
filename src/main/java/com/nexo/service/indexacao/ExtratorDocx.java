package com.nexo.service.indexacao;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/** Texto de DOCX (Word 2007+) pelo Apache POI. O antigo {@code .doc} não é aceito. */
@Component
public class ExtratorDocx implements ExtratorDeTexto {

    @Override
    public Set<String> tiposSuportados() {
        return Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Override
    public String extrair(InputStream arquivo) throws IOException {
        try (XWPFDocument documento = new XWPFDocument(arquivo);
             XWPFWordExtractor extrator = new XWPFWordExtractor(documento)) {
            return extrator.getText();
        }
    }
}
