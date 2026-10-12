package com.nexo.dto.pdi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Dados para criar um PDI. O gestor não vem do cliente: é sempre quem cria,
 * e o PDI sempre nasce {@code ATIVO}.
 *
 * @param colaboradorId usuário do mesmo tenant dono do plano
 * @param ciclo         rótulo livre do ciclo (ex.: "2026", "2026-S2"); único por colaborador
 * @param inicio        início do ciclo
 * @param fim           fim do ciclo (não pode ser antes do início)
 */
public record CriarPdiRequest(
        @NotNull(message = "colaborador é obrigatório")
        UUID colaboradorId,

        @NotBlank(message = "ciclo é obrigatório")
        @Size(max = 20, message = "ciclo deve ter no máximo 20 caracteres")
        String ciclo,

        @NotNull(message = "início é obrigatório")
        LocalDate inicio,

        @NotNull(message = "fim é obrigatório")
        LocalDate fim) {
}
