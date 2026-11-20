package com.nexo.repository;

import com.nexo.entity.Conteudo;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Fábrica da {@link Specification} da listagem de conteúdos. */
public final class ConteudoSpecifications {

    private ConteudoSpecifications() {
    }

    /** Tira o que só a gestão pode ver (ex.: salários). */
    public static Specification<Conteudo> semRestritosAGestao() {
        return (root, query, cb) -> cb.isFalse(root.get("apenasAdmin"));
    }

    public static Specification<Conteudo> comFiltro(ConteudoFiltro filtro, UUID tenantId) {
        return (root, query, cb) -> {
            if (!AtribuicaoSpecifications.ehContagem(query)) {
                root.fetch("categoria", JoinType.LEFT);
                root.fetch("area", JoinType.LEFT);
            }

            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.equal(root.get("tenant").get("id"), tenantId));
            if (StringUtils.hasText(filtro.titulo())) {
                predicados.add(cb.like(
                        cb.lower(root.get("titulo")),
                        "%" + filtro.titulo().trim().toLowerCase() + "%"));
            }
            if (filtro.categoriaId() != null) {
                predicados.add(cb.equal(root.get("categoria").get("id"), filtro.categoriaId()));
            }
            if (filtro.areaId() != null) {
                predicados.add(cb.equal(root.get("area").get("id"), filtro.areaId()));
            }
            if (filtro.tipo() != null) {
                predicados.add(cb.equal(root.get("tipo"), filtro.tipo()));
            }
            if (filtro.status() != null) {
                predicados.add(cb.equal(root.get("status"), filtro.status()));
            }
            if (filtro.catalogo() != null) {
                predicados.add(cb.equal(root.get("catalogo"), filtro.catalogo()));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
