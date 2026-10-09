package com.nexo.service.recomendacao;

import com.nexo.entity.Trilha;
import com.nexo.repository.CriterioRecomendacao;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.util.UUID;

/**
 * Uma forma de colocar as trilhas recomendadas primeiro na listagem. Novos
 * critérios entram como novas implementações (beans), sem mudar o service.
 */
public interface EstrategiaRecomendacao {

    CriterioRecomendacao criterio();

    /**
     * Prioridade de cada trilha na ordenação (menor vem primeiro), ou
     * {@code null} quando não há o que recomendar para este usuário.
     */
    Expression<Integer> prioridade(Root<Trilha> trilha, CriteriaBuilder cb, Contexto contexto);

    /** Dados do usuário autenticado que as estratégias podem usar. */
    record Contexto(UUID areaDoUsuarioId) {
    }
}
