package com.nexo.repository;

import com.nexo.entity.HistoricoInteracao;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoricoInteracaoRepository extends JpaRepository<HistoricoInteracao, UUID> {

    @EntityGraph(attributePaths = "usuario")
    Page<HistoricoInteracao> findAllByTenantId(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = "usuario")
    Page<HistoricoInteracao> findAllByTenantIdAndUsuarioId(UUID tenantId, UUID usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = "usuario")
    List<HistoricoInteracao> findTop10ByTenantIdAndUsuarioIdOrderByCriadoEmDescIdDesc(
            UUID tenantId, UUID usuarioId);

    @Query(value = """
            SELECT trilha_id,
                   (ARRAY_AGG(trilha_nome ORDER BY criado_em DESC))[1] AS trilha_nome,
                   COUNT(*) AS total_iniciadas
              FROM (
                    SELECT detalhes ->> 'trilhaId' AS trilha_id,
                           detalhes ->> 'trilhaNome' AS trilha_nome,
                           criado_em
                      FROM historico_interacao
                     WHERE tenant_id = :tenantId
                       AND tipo = 'TRILHA_INICIADA'
                       AND detalhes ->> 'trilhaId' IS NOT NULL
                   ) eventos
             GROUP BY trilha_id
             ORDER BY COUNT(*) DESC, MAX(criado_em) DESC
             LIMIT :limite
            """, nativeQuery = true)
    List<Object[]> encontrarTrilhasMaisIniciadas(
            @Param("tenantId") UUID tenantId,
            @Param("limite") int limite);

}
