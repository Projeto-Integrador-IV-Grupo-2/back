package com.nexo.repository;

import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.TrilhaAtribuicao;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Fábricas de {@link Specification} para consultas sobre {@code trilha_atribuicao}.
 */
public final class AtribuicaoSpecifications {

    private AtribuicaoSpecifications() {
    }

    /** Acompanhamento: atribuições do tenant, com os filtros informados. */
    public static Specification<TrilhaAtribuicao> comFiltro(AtribuicaoFiltro filtro, UUID tenantId, LocalDate hoje) {
        return (root, query, cb) -> {
            buscarJuntoUsuarioETrilha(root, query);

            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.equal(root.get("tenant").get("id"), tenantId));
            if (filtro != null) {
                if (filtro.trilhaId() != null) {
                    predicados.add(cb.equal(root.get("trilha").get("id"), filtro.trilhaId()));
                }
                if (filtro.areaId() != null) {
                    predicados.add(cb.equal(root.get("usuario").get("area").get("id"), filtro.areaId()));
                }
                if (filtro.usuarioId() != null) {
                    predicados.add(cb.equal(root.get("usuario").get("id"), filtro.usuarioId()));
                }
                if (filtro.status() != null) {
                    predicados.add(statusEfetivo(root, cb, filtro.status(), hoje));
                }
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    /**
     * Predicado do status efetivo: a mesma regra de {@code RegraDeAtraso}, mas
     * aplicada no banco. Atrasada = gravada como ATRASADA, ou prazo vencido sem
     * conclusão; os demais status só valem quando a atribuição não está atrasada.
     */
    public static Predicate statusEfetivo(Path<TrilhaAtribuicao> atribuicao, CriteriaBuilder cb,
                                          StatusAtribuicao status, LocalDate hoje) {
        Path<StatusAtribuicao> gravado = atribuicao.get("status");
        Path<LocalDate> prazo = atribuicao.get("prazo");
        Predicate vencida = cb.and(
                cb.notEqual(gravado, StatusAtribuicao.CONCLUIDA),
                cb.isNotNull(prazo),
                cb.lessThan(prazo, hoje));
        Predicate atrasada = cb.or(cb.equal(gravado, StatusAtribuicao.ATRASADA), vencida);

        return switch (status) {
            case ATRASADA -> atrasada;
            case CONCLUIDA -> cb.equal(gravado, StatusAtribuicao.CONCLUIDA);
            case NAO_INICIADA, EM_ANDAMENTO -> cb.and(cb.equal(gravado, status), cb.not(atrasada));
        };
    }

    /**
     * Evita uma consulta por linha ao montar a resposta. O fetch fica de fora
     * da consulta de contagem da paginação, onde o Hibernate não o aceita.
     */
    private static void buscarJuntoUsuarioETrilha(Root<TrilhaAtribuicao> root, CriteriaQuery<?> query) {
        if (ehContagem(query)) {
            return;
        }
        Fetch<Object, Object> usuario = root.fetch("usuario", JoinType.INNER);
        usuario.fetch("area", JoinType.LEFT);
        root.fetch("trilha", JoinType.INNER);
    }

    static boolean ehContagem(CriteriaQuery<?> query) {
        Class<?> tipo = query.getResultType();
        return tipo == Long.class || tipo == long.class;
    }
}
