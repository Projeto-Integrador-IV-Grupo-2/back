package com.nexo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Upload de documentos lido de {@code app.documentos.*}.
 *
 * @param tamanhoMaximo maior arquivo aceito ({@code DOCUMENTOS_TAMANHO_MAXIMO}, padrão 10MB)
 */
@ConfigurationProperties(prefix = "app.documentos")
public record DocumentosProperties(DataSize tamanhoMaximo) {

    public DocumentosProperties {
        tamanhoMaximo = tamanhoMaximo == null ? DataSize.ofMegabytes(10) : tamanhoMaximo;
    }
}
