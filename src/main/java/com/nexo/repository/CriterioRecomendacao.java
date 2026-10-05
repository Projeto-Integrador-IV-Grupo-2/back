package com.nexo.repository;

/**
 * Critérios de recomendação aceitos em {@code GET /api/trilhas?recomendar=...}.
 * Cada um tem uma estratégia em {@code com.nexo.service.recomendacao}.
 */
public enum CriterioRecomendacao {

    /** Trilhas da área do usuário autenticado primeiro. */
    AREA
}
