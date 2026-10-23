package com.nexo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Onde ficam os binários dos arquivos, lido de {@code app.armazenamento.*}.
 *
 * @param tipo      {@code local} (pasta do servidor); outros tipos entram na
 *                  {@code ArmazenamentoFactory}
 * @param diretorio pasta usada pelo tipo {@code local} ({@code ARMAZENAMENTO_DIRETORIO})
 */
@ConfigurationProperties(prefix = "app.armazenamento")
public record ArmazenamentoProperties(String tipo, String diretorio) {

    public ArmazenamentoProperties {
        tipo = tipo == null || tipo.isBlank() ? "local" : tipo.trim();
        diretorio = diretorio == null || diretorio.isBlank() ? "./data/arquivos" : diretorio.trim();
    }
}
