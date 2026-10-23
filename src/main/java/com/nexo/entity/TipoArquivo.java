package com.nexo.entity;

/**
 * Formato de um arquivo anexado a um conteúdo (enum PostgreSQL
 * {@code tipo_arquivo}). Cada formato leva o tipo MIME gravado em
 * {@code arquivo.mime_type} — o mesmo que os extratores da indexação usam.
 */
public enum TipoArquivo {
    PDF("application/pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    TXT("text/plain"),
    MD("text/markdown");

    private final String mimeType;

    TipoArquivo(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }
}
