package com.nexo.dto.conteudo;

import com.nexo.entity.TipoConteudo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Corpo de criação e edição de conteúdo. Qual campo de material é obrigatório
 * depende do {@code tipo} — a regra fica no {@code ConteudoFactory}:
 * {@code VIDEO} exige {@code videoUrl} (link do Vimeo), {@code TEXTO} exige
 * {@code corpo} e {@code LINK} exige {@code linkUrl}.
 *
 * <p>Duração e miniatura não vêm do cliente: são preenchidas pela API a partir
 * do oEmbed do Vimeo. A situação (rascunho/publicado) também fica de fora —
 * todo conteúdo nasce rascunho e só muda por {@code PATCH /publicar}.</p>
 */
public record ConteudoRequest(
        @NotBlank(message = "título é obrigatório")
        @Size(max = 200, message = "título deve ter no máximo 200 caracteres")
        String titulo,

        @Size(max = 2000, message = "descrição deve ter no máximo 2000 caracteres")
        String descricao,

        @NotNull(message = "tipo é obrigatório")
        TipoConteudo tipo,

        UUID categoriaId,

        UUID areaId,

        @Size(max = 500, message = "link do vídeo deve ter no máximo 500 caracteres")
        String videoUrl,

        String corpo,

        @Size(max = 500, message = "link deve ter no máximo 500 caracteres")
        String linkUrl) {
}
