package com.nexo.dto.etapa;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Corpo de criação e edição de etapa. A ordem não vem aqui: a etapa nova vai
 * para o fim da trilha, e a posição só muda por {@code PATCH /etapas/ordem}.
 *
 * @param conteudoId conteúdo publicado do mesmo tenant
 * @param titulo     opcional; em branco usa o título do conteúdo
 */
public record EtapaRequest(
        @NotNull(message = "conteúdo é obrigatório")
        UUID conteudoId,

        @Size(max = 200, message = "título deve ter no máximo 200 caracteres")
        String titulo,

        @Size(max = 2000, message = "descrição deve ter no máximo 2000 caracteres")
        String descricao) {
}
