package com.nexo.dto.trilha;

import java.util.UUID;

/**
 * Resposta de {@code POST /api/trilhas/sugestoes/ajuda}.
 *
 * @param texto    resposta da IA em texto simples
 * @param rascunho trilha nova proposta pela IA, quando o pedido foi para criar
 *                 uma; {@code null} nos outros casos. Não é gravado: o front
 *                 abre o cadastro preenchido e quem cria revisa antes de salvar.
 */
public record AjudaTrilhasResponse(String texto, Rascunho rascunho) {

    /**
     * Mesmos campos de {@link CriarTrilhaRequest}, já dentro dos limites dele.
     *
     * @param areaId área do catálogo que a IA indicou pelo nome; {@code null}
     *               quando não indicou ou quando o nome não bate com nenhuma
     */
    public record Rascunho(String nome, String descricao, UUID areaId, Integer prazoDias, String objetivos) {
    }
}
