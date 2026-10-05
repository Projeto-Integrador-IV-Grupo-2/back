package com.nexo.repository;

import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.util.StringUtils;

/**
 * Fábricas de {@link Specification} da listagem de trilhas.
 *
 * <p>Há dois pontos de partida: o catálogo ({@link #comFiltro}) consulta
 * {@code trilha} e usa subconsultas para o progresso do usuário; "minhas
 * trilhas" ({@link #minhas}) consulta {@code trilha_atribuicao} do usuário,
 * porque precisa ordenar pelo prazo, que só existe na atribuição.</p>
 */
public final class TrilhaSpecifications {

    private TrilhaSpecifications() {
    }

    /**
     * Catálogo: trilhas ativas do tenant com os filtros informados.
     *
     * @param prioridade expressão opcional que vem antes da ordenação pedida
     *                   (menor primeiro), usada pelas recomendações; com ela, a
     *                   ordenação do {@code sort} é aplicada aqui, e o
     *                   {@code Pageable} deve ir sem ordenação
     */
    public static Specification<Trilha> comFiltro(TrilhaFiltro filtro, UUID tenantId, UUID usuarioId, LocalDate hoje,
                                                 Sort sort,
                                                 BiFunction<Root<Trilha>, CriteriaBuilder, Expression<Integer>> prioridade) {
        return (root, query, cb) -> {
            if (!AtribuicaoSpecifications.ehContagem(query)) {
                root.fetch("area", JoinType.LEFT);
                if (prioridade != null) {
                    List<Order> ordens = new ArrayList<>();
                    Expression<Integer> peso = prioridade.apply(root, cb);
                    if (peso != null) {
                        ordens.add(cb.asc(peso));
                    }
                    ordens.addAll(QueryUtils.toOrders(sort, root, cb));
                    query.orderBy(ordens);
                }
            }

            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.equal(root.get("tenant").get("id"), tenantId));
            predicados.add(cb.isTrue(root.get("ativo")));
            predicados.addAll(daTrilha(root, cb, filtro));
            if (filtro.status() != null) {
                predicados.add(progressoDoUsuario(root, query, cb, usuarioId, filtro.status(), hoje));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    /**
     * "Minhas trilhas": atribuições do usuário em trilhas ativas, com os
     * filtros informados. Ordena pelo prazo (sem prazo no fim) quando
     * {@code porPrazo}; senão, pelo nome da trilha.
     */
    public static Specification<TrilhaAtribuicao> minhas(TrilhaFiltro filtro, UUID tenantId, UUID usuarioId,
                                                         LocalDate hoje, boolean porPrazo) {
        return (root, query, cb) -> {
            Path<Trilha> trilha = root.get("trilha");
            if (!AtribuicaoSpecifications.ehContagem(query)) {
                root.fetch("trilha", JoinType.INNER).fetch("area", JoinType.LEFT);
                Path<String> nome = trilha.get("nome");
                if (porPrazo) {
                    Path<LocalDate> prazo = root.get("prazo");
                    Expression<Integer> semPrazoNoFim = cb.<Integer>selectCase()
                            .when(cb.isNull(prazo), 1)
                            .otherwise(0);
                    query.orderBy(cb.asc(semPrazoNoFim), cb.asc(prazo), cb.asc(nome));
                } else {
                    query.orderBy(cb.asc(nome));
                }
            }

            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.equal(root.get("tenant").get("id"), tenantId));
            predicados.add(cb.equal(root.get("usuario").get("id"), usuarioId));
            predicados.add(cb.isTrue(trilha.get("ativo")));
            predicados.addAll(daTrilha(trilha, cb, filtro));
            if (filtro.status() != null) {
                predicados.add(AtribuicaoSpecifications.statusEfetivo(root, cb, filtro.status(), hoje));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private static List<Predicate> daTrilha(Path<Trilha> trilha, CriteriaBuilder cb, TrilhaFiltro filtro) {
        List<Predicate> predicados = new ArrayList<>();
        if (StringUtils.hasText(filtro.nome())) {
            predicados.add(cb.like(
                    cb.lower(trilha.get("nome")),
                    "%" + filtro.nome().trim().toLowerCase() + "%"));
        }
        if (filtro.areaId() != null) {
            predicados.add(cb.equal(trilha.get("area").get("id"), filtro.areaId()));
        }
        return predicados;
    }

    /**
     * Status do progresso do usuário na trilha. NAO_INICIADA inclui quem nunca
     * iniciou (sem atribuição), além de atribuições gravadas como não iniciadas.
     */
    private static Predicate progressoDoUsuario(Root<Trilha> trilha, CriteriaQuery<?> query, CriteriaBuilder cb,
                                                UUID usuarioId, StatusAtribuicao status, LocalDate hoje) {
        Subquery<UUID> comStatus = query.subquery(UUID.class);
        Root<TrilhaAtribuicao> atribuicao = comStatus.from(TrilhaAtribuicao.class);
        comStatus.select(atribuicao.get("id")).where(
                cb.equal(atribuicao.get("trilha"), trilha),
                cb.equal(atribuicao.get("usuario").get("id"), usuarioId),
                AtribuicaoSpecifications.statusEfetivo(atribuicao, cb, status, hoje));

        if (status != StatusAtribuicao.NAO_INICIADA) {
            return cb.exists(comStatus);
        }

        Subquery<UUID> qualquer = query.subquery(UUID.class);
        Root<TrilhaAtribuicao> outra = qualquer.from(TrilhaAtribuicao.class);
        qualquer.select(outra.get("id")).where(
                cb.equal(outra.get("trilha"), trilha),
                cb.equal(outra.get("usuario").get("id"), usuarioId));
        return cb.or(cb.not(cb.exists(qualquer)), cb.exists(comStatus));
    }
}
