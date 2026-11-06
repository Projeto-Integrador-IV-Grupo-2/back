package com.nexo.repository;

import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.TrilhaAtribuicao;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrilhaAtribuicaoRepository
        extends JpaRepository<TrilhaAtribuicao, UUID>, JpaSpecificationExecutor<TrilhaAtribuicao> {

    Optional<TrilhaAtribuicao> findByTrilhaIdAndUsuarioId(UUID trilhaId, UUID usuarioId);

    /** Variante explicitamente limitada ao tenant para consumidores de eventos de domínio. */
    Optional<TrilhaAtribuicao> findByTrilhaIdAndTenantIdAndUsuarioId(
            UUID trilhaId, UUID tenantId, UUID usuarioId);

    boolean existsByTrilhaIdAndUsuarioId(UUID trilhaId, UUID usuarioId);

    List<TrilhaAtribuicao> findByTenantIdAndUsuarioId(UUID tenantId, UUID usuarioId);

    /** Quem ainda não concluiu a trilha — os afetados quando as etapas dela mudam. */
    List<TrilhaAtribuicao> findByTrilhaIdAndStatusNot(UUID trilhaId, StatusAtribuicao status);

    /** Variante tenant-scoped para o listener de mudanças nas etapas. */
    List<TrilhaAtribuicao> findByTrilhaIdAndTenantIdAndStatusNot(
            UUID trilhaId, UUID tenantId, StatusAtribuicao status);

    /** Atribuições vencidas ainda não processadas pela rotina diária de atraso. */
    /** Atribuições com prazo entre as datas e nos status informados — o aviso de prazo próximo (issue #78). */
    List<TrilhaAtribuicao> findByPrazoBetweenAndStatusIn(LocalDate inicio, LocalDate fim,
                                                        Collection<StatusAtribuicao> status);

    List<TrilhaAtribuicao> findByPrazoBeforeAndStatusNotIn(
            LocalDate prazo, Collection<StatusAtribuicao> status);

    /** Prazos do colaborador; busca trilha junto para não gerar N+1 no dashboard. */
    @EntityGraph(attributePaths = "trilha")
    List<TrilhaAtribuicao> findByTenantIdAndUsuarioIdAndPrazoBetweenAndStatusNotOrderByPrazoAsc(
            UUID tenantId, UUID usuarioId, LocalDate prazoInicial, LocalDate prazoFinal, StatusAtribuicao status);

    /** Campos mínimos para o dashboard de gestão; a regra de atraso fica no service. */
    @Query("""
            SELECT atribuicao.id AS atribuicaoId,
                   atribuicao.status AS status,
                   atribuicao.prazo AS prazo,
                   area.id AS areaId,
                   area.nome AS areaNome
              FROM TrilhaAtribuicao atribuicao
              JOIN atribuicao.usuario usuario
              LEFT JOIN usuario.area area ON area.tenant.id = :tenantId
             WHERE atribuicao.tenant.id = :tenantId
               AND usuario.tenant.id = :tenantId
             ORDER BY atribuicao.id ASC
            """)
    Slice<AtribuicaoDashboardProjection> buscarDadosDashboardPorTenant(
            @Param("tenantId") UUID tenantId, Pageable pageable);
}
