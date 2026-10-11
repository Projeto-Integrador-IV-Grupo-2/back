package com.nexo.dto.categoria;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de criação e edição de categoria. O tenant vem sempre do usuário autenticado.
 *
 * @param nome      único (sem diferenciar maiúsculas) dentro do tenant
 * @param descricao texto livre, opcional
 */
public record CategoriaRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 100, message = "nome deve ter no máximo 100 caracteres")
        String nome,

        @Size(max = 1000, message = "descrição deve ter no máximo 1000 caracteres")
        String descricao) {
}
