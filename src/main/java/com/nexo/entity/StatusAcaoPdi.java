package com.nexo.entity;

/**
 * Situação de uma ação do PDI (enum {@code status_acao_pdi}). Numa ação
 * {@code TRILHA} ela não é editada à mão: vira {@code CONCLUIDA} quando a
 * trilha é concluída.
 */
public enum StatusAcaoPdi {
    PENDENTE,
    EM_ANDAMENTO,
    CONCLUIDA,
    CANCELADA
}
