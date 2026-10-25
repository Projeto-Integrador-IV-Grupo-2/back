package com.nexo.service.recomendacao;

import com.nexo.entity.Trilha;
import com.nexo.repository.CriterioRecomendacao;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Trilhas da área do usuário primeiro; usuário sem área não recebe prioridade.
 *
 * <p>É também a rede de segurança da sugestão por IA: quando o modelo está
 * fora do ar, {@code GET /api/trilhas/sugestoes} cai aqui e responde 200 com
 * estas indicações, avisando que não vieram da IA.</p>
 */
@Component
public class RecomendacaoPorArea implements EstrategiaRecomendacao {

    static final String MOTIVO_DA_AREA = "Trilha da sua área.";
    static final String MOTIVO_GERAL = "Trilha disponível no catálogo da empresa.";

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

    /** Mesma regra da ordenação, aplicada ao catálogo: área do usuário primeiro, depois por nome. */
    @Override
    public List<TrilhaSugerida> sugerir(DadosParaSugestao dados, int limite) {
        if (limite <= 0) {
            return List.of();
        }
        UUID areaDoUsuario = dados.contexto().areaDoUsuarioId();
        return dados.catalogo().stream()
                .sorted(Comparator
                        .comparingInt((TrilhaCandidata trilha) -> ehDaArea(trilha, areaDoUsuario) ? 0 : 1)
                        .thenComparing(TrilhaCandidata::nome, String.CASE_INSENSITIVE_ORDER))
                .limit(limite)
                .map(trilha -> new TrilhaSugerida(trilha,
                        ehDaArea(trilha, areaDoUsuario) ? MOTIVO_DA_AREA : MOTIVO_GERAL))
                .toList();
    }

    private static boolean ehDaArea(TrilhaCandidata trilha, UUID areaDoUsuarioId) {
        return areaDoUsuarioId != null && areaDoUsuarioId.equals(trilha.areaId());
    }
}
