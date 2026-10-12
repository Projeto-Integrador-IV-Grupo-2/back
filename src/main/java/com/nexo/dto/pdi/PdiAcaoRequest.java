package com.nexo.dto.pdi;

import com.nexo.dto.pdi.validation.AcaoCoerenteComTipo;
import com.nexo.entity.TipoAcaoPdi;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ação de um item do PDI.
 *
 * <p>{@code TRILHA} exige {@code trilhaId} (a descrição é opcional); os demais
 * tipos exigem {@code descricao} e não aceitam trilha — ver
 * {@link AcaoCoerenteComTipo}. O prazo é obrigatório porque vira o prazo da
 * atribuição da trilha ao colaborador.</p>
 */
@AcaoCoerenteComTipo
public record PdiAcaoRequest(
        @NotNull(message = "tipo é obrigatório")
        TipoAcaoPdi tipo,

        UUID trilhaId,

        @NotNull(message = "prazo é obrigatório")
        LocalDate prazo,

        @Size(max = 1000, message = "descrição deve ter no máximo 1000 caracteres")
        String descricao) {
}
