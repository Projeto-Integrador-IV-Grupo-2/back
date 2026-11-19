package com.nexo.dto.trilha;

import java.util.List;
import java.util.UUID;

/**
 * Resposta de {@code POST /api/trilhas/sugestoes/ajuda}.
 *
 * @param texto    resposta da IA em texto simples
 * @param rascunho trilha nova montada pela IA, quando o pedido foi para criar
 *                 uma; {@code null} nos outros casos. Não é gravado: o front
 *                 mostra a trilha, quem cria revisa e salva pelos endpoints de
 *                 sempre ({@code POST /api/trilhas} e as etapas).
 */
public record AjudaTrilhasResponse(String texto, Rascunho rascunho) {

    /**
     * Trilha completa: os campos de {@link CriarTrilhaRequest}, já dentro dos
     * limites dele, mais as etapas e o que falta na base para cobrir o tema.
     *
     * @param areaId             área que a IA indicou pelo nome; {@code null}
     *                           quando não indicou ou o nome não bate com nenhuma
     * @param etapas             etapas em ordem, cada uma com um conteúdo
     *                           publicado do tenant — prontas para
     *                           {@code POST /api/trilhas/{id}/etapas}
     * @param conteudosFaltando  temas que a trilha precisaria e que ainda não
     *                           têm conteúdo publicado na base
     */
    public record Rascunho(String nome, String descricao, UUID areaId, Integer prazoDias, String objetivos,
                           List<Etapa> etapas, List<String> conteudosFaltando) {

        public Rascunho {
            etapas = etapas == null ? List.of() : List.copyOf(etapas);
            conteudosFaltando = conteudosFaltando == null ? List.of() : List.copyOf(conteudosFaltando);
        }
    }

    /**
     * Uma etapa do rascunho.
     *
     * @param conteudoId     conteúdo publicado do tenant (vindo do catálogo enviado à IA)
     * @param conteudoTitulo título do conteúdo, lido do banco — não do modelo
     * @param conteudoTipo   tipo do conteúdo ({@code VIDEO}, {@code TEXTO}…)
     */
    public record Etapa(UUID conteudoId, String conteudoTitulo, String conteudoTipo, String titulo,
                        String descricao) {
    }
}
