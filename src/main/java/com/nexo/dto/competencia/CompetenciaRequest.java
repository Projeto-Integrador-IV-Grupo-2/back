package com.nexo.dto.competencia;

import com.nexo.entity.TipoCompetencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada de uma competência — os mesmos na criação e na edição. A
 * situação (ativa/inativa) só muda pelos endpoints de ativar/inativar.
 *
 * @param nome      único dentro do tenant
 * @param descricao texto livre, opcional
 * @param tipo      técnica ou comportamental
 */
public record CompetenciaRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres")
        String nome,

        @Size(max = 1000, message = "descrição deve ter no máximo 1000 caracteres")
        String descricao,

        @NotNull(message = "tipo é obrigatório")
        TipoCompetencia tipo) {
}
