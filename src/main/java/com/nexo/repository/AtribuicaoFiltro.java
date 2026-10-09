package com.nexo.repository;

import com.nexo.entity.StatusAtribuicao;
import java.util.UUID;

/**
 * Filtros do acompanhamento ({@code GET /api/atribuicoes} e exportação), todos
 * opcionais e combinados com E.
 *
 * @param trilhaId  trilha atribuída
 * @param areaId    área do colaborador
 * @param usuarioId colaborador
 * @param status    status efetivo (prazo vencido e não concluída conta como ATRASADA)
 */
public record AtribuicaoFiltro(
        UUID trilhaId,
        UUID areaId,
        UUID usuarioId,
        StatusAtribuicao status) {

    public static AtribuicaoFiltro vazio() {
        return new AtribuicaoFiltro(null, null, null, null);
    }

    public static AtribuicaoFiltro doColaborador(UUID usuarioId) {
        return new AtribuicaoFiltro(null, null, usuarioId, null);
    }
}
