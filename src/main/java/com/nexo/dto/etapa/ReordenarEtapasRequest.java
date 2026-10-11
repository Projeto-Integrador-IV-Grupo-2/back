package com.nexo.dto.etapa;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * Nova ordem das etapas: todos os ids das etapas da trilha, na sequência
 * desejada. A primeira passa a ter ordem 1.
 */
public record ReordenarEtapasRequest(
        @NotEmpty(message = "informe as etapas na nova ordem")
        List<UUID> etapaIds) {
}
