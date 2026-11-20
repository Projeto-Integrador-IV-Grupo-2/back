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
 * {@link AcaoCoerenteComTipo}. Na ação {@code TRILHA} o prazo é opcional:
 * sem ele vale o prazo da própria trilha (ver {@code AcaoDeTrilha}). Nos
 * demais tipos ele é obrigatório.</p>
 */
@AcaoCoerenteComTipo
public record PdiAcaoRequest(
        @NotNull(message = "tipo é obrigatório")
        TipoAcaoPdi tipo,

        UUID trilhaId,

        LocalDate prazo,

        @Size(max = 1000, message = "descrição deve ter no máximo 1000 caracteres")
        String descricao) {
}
