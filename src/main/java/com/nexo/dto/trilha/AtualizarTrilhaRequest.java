package com.nexo.dto.trilha;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Corpo de {@code PUT /api/trilhas/{id}}.
 *
 * <p>Mesmos campos editáveis do cadastro. {@code ativo} fica de fora de
 * propósito: a situação só muda pelos endpoints de ativar/inativar, que
 * publicam evento e têm regra própria.</p>
 *
 * @param nome      nome da trilha — continua único (ignorando maiúsculas) dentro do tenant
 * @param areaId    área da trilha; nulo = trilha multi-área
 * @param prazoDias prazo sugerido em dias, contado a partir do início pelo usuário
 * @param objetivos texto livre
 * @param descricao texto livre
 */
public record AtualizarTrilhaRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 200, message = "nome deve ter no máximo 200 caracteres")
        String nome,

        UUID areaId,

        @Positive(message = "prazo deve ser maior que zero")
        Integer prazoDias,

        @Size(max = 2000, message = "objetivos deve ter no máximo 2000 caracteres")
        String objetivos,

        @Size(max = 2000, message = "descrição deve ter no máximo 2000 caracteres")
        String descricao) {
}
