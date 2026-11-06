package com.nexo.entity;

/** Tipo de uma notificação (coluna {@code notificacao.tipo}, issue #78). */
public enum TipoNotificacao {
    /** Uma trilha foi atribuída ao colaborador. */
    TRILHA_ATRIBUIDA,
    /** O prazo de uma trilha do colaborador vence em poucos dias. */
    PRAZO_PROXIMO,
    /** O prazo de uma trilha do colaborador venceu. */
    PRAZO_VENCIDO,
    /** Alguém da área do gestor concluiu uma trilha. */
    TRILHA_CONCLUIDA,
    /** Um conteúdo novo foi publicado na área do colaborador. */
    CONTEUDO_PUBLICADO,
    /** Uma trilha que o colaborador fazia foi inativada. */
    TRILHA_INATIVADA
}
