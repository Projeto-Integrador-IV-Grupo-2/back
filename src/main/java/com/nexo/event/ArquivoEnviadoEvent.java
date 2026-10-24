package com.nexo.event;

import java.util.UUID;

/**
 * Um arquivo (PDF, DOCX, TXT ou MD) foi anexado a um conteúdo.
 *
 * <p>Contrato para o upload de documentos (issue #55): quem grava o arquivo só
 * publica este evento. A indexação da IA (issue #58) escuta e reindexa o
 * conteúdo, se ele estiver publicado.</p>
 *
 * @param arquivoId  arquivo enviado
 * @param conteudoId conteúdo dono do arquivo
 * @param tenantId   tenant dele
 */
public record ArquivoEnviadoEvent(UUID arquivoId, UUID conteudoId, UUID tenantId) {
}
