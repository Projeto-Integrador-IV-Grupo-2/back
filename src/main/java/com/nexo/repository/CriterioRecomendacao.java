package com.nexo.repository;

/**
 * Critérios de recomendação aceitos em {@code GET /api/trilhas?recomendar=...}.
 * Cada um tem uma estratégia em {@code com.nexo.service.recomendacao}.
 */
public enum CriterioRecomendacao {

    /** Trilhas da área do usuário autenticado primeiro. */
    AREA,

    /**
     * Trilhas que a IA indicou para o usuário primeiro, na ordem em que ela
     * indicou. Na listagem vale só o que já foi calculado por
     * {@code GET /api/trilhas/sugestoes}: ordenar uma página nunca chama o
     * modelo.
     */
    IA
}
