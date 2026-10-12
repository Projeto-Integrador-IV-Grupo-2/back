package com.nexo.service.recomendacao;

import com.nexo.exception.ParametroInvalidoException;
import com.nexo.repository.CriterioRecomendacao;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Entrega a estratégia de recomendação de cada critério. As estratégias são
 * beans do Spring e chegam aqui sozinhas: um critério novo só precisa de uma
 * nova implementação de {@link EstrategiaRecomendacao}.
 */
@Component
public class EstrategiaRecomendacaoFactory {

    private final Map<CriterioRecomendacao, EstrategiaRecomendacao> estrategias =
            new EnumMap<>(CriterioRecomendacao.class);

    public EstrategiaRecomendacaoFactory(List<EstrategiaRecomendacao> disponiveis) {
        disponiveis.forEach(estrategia -> estrategias.put(estrategia.criterio(), estrategia));
    }

    public EstrategiaRecomendacao para(CriterioRecomendacao criterio) {
        EstrategiaRecomendacao estrategia = estrategias.get(criterio);
        if (estrategia == null) {
            throw new ParametroInvalidoException("Critério de recomendação não suportado: " + criterio);
        }
        return estrategia;
    }
}
