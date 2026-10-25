package com.nexo.service.recomendacao;

import com.nexo.entity.Trilha;
import com.nexo.repository.CriterioRecomendacao;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.UUID;

/**
 * Uma forma de recomendar trilhas a um usuário. Novos critérios entram como
 * novas implementações (beans), sem mudar o service.
 *
 * <p>Cada estratégia serve a dois usos: {@link #prioridade} coloca as trilhas
 * recomendadas na frente da listagem, e {@link #sugerir} monta a lista curta
 * de {@code GET /api/trilhas/sugestoes}, com o motivo de cada indicação.</p>
 */
public interface EstrategiaRecomendacao {

    CriterioRecomendacao criterio();

    /**
     * Prioridade de cada trilha na ordenação (menor vem primeiro), ou
     * {@code null} quando não há o que recomendar para este usuário.
     */
    Expression<Integer> prioridade(Root<Trilha> trilha, CriteriaBuilder cb, Contexto contexto);

    /**
     * As trilhas mais indicadas para o usuário, cada uma com um motivo curto
     * em português.
     *
     * @param catalogo trilhas candidatas — já filtradas por tenant, ativas e
     *                 não concluídas pelo usuário. A estratégia só pode
     *                 devolver trilhas que estejam aqui.
     * @param limite   máximo de sugestões
     */
    List<TrilhaSugerida> sugerir(Contexto contexto, List<Trilha> catalogo, int limite);

    /**
     * Dados do usuário autenticado que as estratégias podem usar. Carrega só
     * identificadores: o que cada estratégia precisa saber além disso ela
     * busca por conta própria.
     */
    record Contexto(UUID tenantId, UUID usuarioId, UUID areaDoUsuarioId) {
    }
}
