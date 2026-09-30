package com.nexo.dto.area;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada de uma Área, usados tanto na criação quanto na edição —
 * os dois casos recebem exatamente os mesmos campos.
 *
 * <p>Não há campo de tenant: a área é sempre criada/editada no tenant do
 * usuário autenticado, resolvido pelo {@code AreaService}. O campo
 * {@code ativo} também fica de fora de propósito — a situação só muda pelos
 * endpoints de ativar/inativar, que aplicam a regra de vínculo com usuários.</p>
 *
 * @param nome      nome da área — único dentro do tenant
 * @param descricao texto livre, opcional (em branco é gravado como nulo)
 */
public record AreaRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 100, message = "nome deve ter no máximo 100 caracteres")
        String nome,

        @Size(max = 1000, message = "descrição deve ter no máximo 1000 caracteres")
        String descricao) {
}
