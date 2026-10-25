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
     * <p>Recebe os dados já lidos do banco, e não repositories: a
     * implementação que fala com o modelo roda <strong>fora de
     * transação</strong>, então não pode disparar lazy loading. Só trilhas do
     * catálogo de {@code dados} podem ser devolvidas.</p>
     *
     * @param limite máximo de sugestões
     */
    List<TrilhaSugerida> sugerir(DadosParaSugestao dados, int limite);

    /**
     * Dados do usuário autenticado que as estratégias usam na ordenação.
     * Carrega só identificadores; o que o prompt precisa saber vai no
     * {@link PerfilDoUsuario}.
     */
    record Contexto(UUID tenantId, UUID usuarioId, UUID areaDoUsuarioId) {
    }
}
