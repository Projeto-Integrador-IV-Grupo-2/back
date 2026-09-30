package com.nexo.repository;

import com.nexo.entity.Usuario;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class UsuarioSpecifications {

    private UsuarioSpecifications() {
    }

    public static Specification<Usuario> comFiltro(UsuarioFiltro filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();

            if (filtro != null) {
                if (StringUtils.hasText(filtro.nome())) {
                    predicados.add(cb.like(
                            cb.lower(root.get("nomeCompleto")),
                            "%" + filtro.nome().trim().toLowerCase() + "%"));
                }
                if (StringUtils.hasText(filtro.email())) {
                    predicados.add(cb.like(
                            cb.lower(root.get("email")),
                            "%" + filtro.email().trim().toLowerCase() + "%"));
                }
                if (filtro.areaId() != null) {
                    predicados.add(cb.equal(root.get("area").get("id"), filtro.areaId()));
                }
                if (StringUtils.hasText(filtro.departamento())) {
                    predicados.add(cb.like(
                            cb.lower(root.join("area", JoinType.LEFT).get("nome")),
                            "%" + filtro.departamento().trim().toLowerCase() + "%"));
                }
                if (filtro.papel() != null) {
                    predicados.add(cb.equal(root.get("papel"), filtro.papel()));
                }
                if (filtro.situacao() != null) {
                    predicados.add(cb.equal(root.get("ativo"), filtro.situacao().isAtivo()));
                }
            }

            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
