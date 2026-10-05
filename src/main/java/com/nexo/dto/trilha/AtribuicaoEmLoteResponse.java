package com.nexo.dto.trilha;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de {@code POST /api/trilhas/{id}/atribuicoes}.
 *
 * <p>Atribuir é idempotente por colaborador: quem já tinha a trilha é contado
 * em {@code ignorados} e mantém o progresso que já fez. Os ids de quem foi
 * efetivamente atribuído vão na resposta para o front conseguir dar um retorno
 * específico sem precisar reconsultar.</p>
 *
 * @param atribuidos quantos passaram a ter a trilha agora
 * @param ignorados  quantos já tinham e foram deixados como estavam
 * @param usuarioIds ids dos que foram atribuídos agora
 */
public record AtribuicaoEmLoteResponse(int atribuidos, int ignorados, List<UUID> usuarioIds) {

    public static AtribuicaoEmLoteResponse de(List<UUID> atribuidos, int ignorados) {
        return new AtribuicaoEmLoteResponse(atribuidos.size(), ignorados, List.copyOf(atribuidos));
    }
}
