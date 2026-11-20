package com.nexo.entity;

/**
 * Onde o conteúdo vive. São catálogos separados:
 *
 * <ul>
 *   <li>{@link #TRILHA}: material de estudo usado nas etapas das trilhas
 *       (tela "Conteúdos de trilha");</li>
 *   <li>{@link #CONHECIMENTO}: acervo da empresa consultado pelo assistente de
 *       IA (tela "Conhecimento"). Não entra em trilha.</li>
 * </ul>
 */
public enum CatalogoConteudo {
    TRILHA,
    CONHECIMENTO
}
