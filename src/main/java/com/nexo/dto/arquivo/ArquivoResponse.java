package com.nexo.dto.arquivo;

import com.nexo.entity.Arquivo;
import com.nexo.entity.TipoArquivo;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dados de saída de um arquivo anexado. A chave de armazenamento nunca sai
 * da API: o download é sempre por {@code GET /api/arquivos/{id}/download}.
 */
public record ArquivoResponse(
        UUID id,
        UUID conteudoId,
        String nomeOriginal,
        TipoArquivo tipo,
        String mimeType,
        long tamanhoBytes,
        OffsetDateTime criadoEm) {

    public static ArquivoResponse de(Arquivo arquivo) {
        return new ArquivoResponse(
                arquivo.getId(),
                arquivo.getConteudo().getId(),
                arquivo.getNomeOriginal(),
                arquivo.getTipo(),
                arquivo.getMimeType(),
                arquivo.getTamanhoBytes(),
                arquivo.getCriadoEm());
    }
}
