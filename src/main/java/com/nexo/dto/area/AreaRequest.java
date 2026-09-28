package com.nexo.dto.area;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada de uma Área, usados tanto na criação quanto na edição —
 * os dois casos recebem exatamente o mesmo campo.
 *
 * <p>Não há campo de tenant: a área é sempre criada/editada no tenant do
 * usuário autenticado, resolvido pelo {@code AreaService}. O campo
 * {@code ativo} também fica de fora de propósito — a situação só muda pelos
 * endpoints de ativar/inativar, que aplicam a regra de vínculo com usuários.</p>
 *
 * @param nome nome da área — único dentro do tenant
 */
public record AreaRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 100, message = "nome deve ter no máximo 100 caracteres")
        String nome) {
}
