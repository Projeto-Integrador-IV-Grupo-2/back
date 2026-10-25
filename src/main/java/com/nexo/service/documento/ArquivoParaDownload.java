package com.nexo.service.documento;

import java.io.InputStream;

/**
 * O que o controller precisa para responder um download: o stream aberto no
 * armazenamento e os dados para os cabeçalhos. Quem recebe fecha o stream.
 */
public record ArquivoParaDownload(String nomeOriginal, String mimeType, long tamanhoBytes, InputStream conteudo) {
}
