package com.nexo.repository;

import com.nexo.entity.StatusAtribuicao;
import java.util.UUID;

/**
 * Filtros de {@code GET /api/trilhas}, todos opcionais e combinados com E.
 *
 * @param nome       trecho do nome da trilha (sem diferenciar maiúsculas)
 * @param areaId     área da trilha
 * @param status     status efetivo do progresso do usuário autenticado na trilha;
 *                   {@code NAO_INICIADA} inclui as trilhas que ele nunca iniciou
 * @param minhas     só as trilhas que o usuário iniciou ou recebeu
 * @param recomendar critério que coloca as trilhas recomendadas primeiro
 */
public record TrilhaFiltro(
        String nome,
        UUID areaId,
        StatusAtribuicao status,
        Boolean minhas,
        CriterioRecomendacao recomendar) {

    public static TrilhaFiltro vazio() {
        return new TrilhaFiltro(null, null, null, null, null);
    }

    public boolean somenteMinhas() {
        return Boolean.TRUE.equals(minhas);
    }

    public TrilhaFiltro comArea(UUID novaAreaId) {
        return new TrilhaFiltro(nome, novaAreaId, status, minhas, recomendar);
    }
}
