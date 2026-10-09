package com.nexo.service.exportacao;

/**
 * Arquivo pronto para download.
 *
 * @param conteudo    bytes do arquivo
 * @param contentType tipo enviado no cabeçalho Content-Type
 * @param nome        nome sugerido no Content-Disposition
 */
public record ArquivoExportado(byte[] conteudo, String contentType, String nome) {
}
