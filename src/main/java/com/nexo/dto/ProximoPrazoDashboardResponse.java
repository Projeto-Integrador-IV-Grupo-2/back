package com.nexo.dto;

import com.nexo.entity.StatusAtribuicao;
import java.time.LocalDate;
import java.util.UUID;

/** Trilha atribuída ao usuário com prazo nos próximos sete dias. */
public record ProximoPrazoDashboardResponse(
        UUID atribuicaoId,
        UUID trilhaId,
        String trilhaNome,
        LocalDate prazo,
        StatusAtribuicao status,
        int percentual) {
}
