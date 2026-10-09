package com.nexo.dto.trilha;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Corpo de {@code POST /api/trilhas/{id}/atribuicoes}.
 *
 * <p>O destino é <strong>ou</strong> uma lista de colaboradores <strong>ou</strong>
 * uma área inteira — nunca os dois, nunca nenhum. Isso é validado aqui, e não
 * no service, para o cliente receber 400 (requisição malformada) em vez de 409
 * (regra de negócio violada).</p>
 *
 * @param usuarioIds colaboradores que receberão a trilha
 * @param areaId     área cujos colaboradores ativos receberão a trilha
 * @param prazo      data limite; quando ausente, cai no {@code prazoDias} da trilha
 */
public record AtribuirTrilhaRequest(
        List<@NotNull(message = "usuarioIds não pode conter nulo") UUID> usuarioIds,

        UUID areaId,

        @FutureOrPresent(message = "prazo não pode ser no passado")
        LocalDate prazo) {

    public boolean temUsuarios() {
        return usuarioIds != null && !usuarioIds.isEmpty();
    }

    public boolean temArea() {
        return areaId != null;
    }

    @AssertTrue(message = "informe usuarioIds ou areaId, nunca os dois")
    public boolean isDestinoValido() {
        return temUsuarios() ^ temArea();
    }
}
