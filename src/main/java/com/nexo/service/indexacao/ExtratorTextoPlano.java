package com.nexo.service.indexacao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * TXT e Markdown, lidos como UTF-8. O Markdown entra como está: a marcação
 * (#, *, links) não atrapalha o embedding e ainda dá contexto ao modelo.
 */
@Component
public class ExtratorTextoPlano implements ExtratorDeTexto {

    @Override
    public Set<String> tiposSuportados() {
        return Set.of("text/plain", "text/markdown", "text/x-markdown");
    }

    @Override
    public String extrair(InputStream arquivo) throws IOException {
        String texto = new String(arquivo.readAllBytes(), StandardCharsets.UTF_8);
        // BOM do UTF-8, comum em arquivos salvos no Bloco de Notas.
        return texto.startsWith("﻿") ? texto.substring(1) : texto;
    }
}
