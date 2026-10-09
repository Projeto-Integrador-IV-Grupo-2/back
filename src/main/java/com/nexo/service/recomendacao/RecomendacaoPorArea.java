package com.nexo.service.recomendacao;

import com.nexo.entity.Trilha;
import com.nexo.repository.CriterioRecomendacao;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Component;

/** Trilhas da área do usuário primeiro; usuário sem área não recebe prioridade. */
@Component
public class RecomendacaoPorArea implements EstrategiaRecomendacao {

    @Override
    public CriterioRecomendacao criterio() {
        return CriterioRecomendacao.AREA;
    }

    @Override
    public Expression<Integer> prioridade(Root<Trilha> trilha, CriteriaBuilder cb, Contexto contexto) {
        if (contexto.areaDoUsuarioId() == null) {
            return null;
        }
        return cb.<Integer>selectCase()
                .when(cb.equal(trilha.get("area").get("id"), contexto.areaDoUsuarioId()), 0)
                .otherwise(1);
    }
}
