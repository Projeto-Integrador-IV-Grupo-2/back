package com.nexo.service.indexacao;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Extrai o texto puro de um arquivo, para ser dividido e indexado.
 *
 * <p>Implementações são singletons do Spring e sem estado. Quem escolhe o
 * extrator certo é a {@link ExtratorDeTextoFactory}.</p>
 */
public interface ExtratorDeTexto {

    /** Tipos MIME atendidos, em minúsculas e sem parâmetros (ex.: {@code application/pdf}). */
    Set<String> tiposSuportados();

    /** Lê o arquivo inteiro e devolve o texto. Não fecha o stream. */
    String extrair(InputStream arquivo) throws IOException;
}
