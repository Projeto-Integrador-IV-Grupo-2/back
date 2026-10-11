package com.nexo.repository;

import com.nexo.entity.TrilhaEtapa;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrilhaEtapaRepository extends JpaRepository<TrilhaEtapa, UUID> {

    @EntityGraph(attributePaths = "conteudo")
    List<TrilhaEtapa> findByTrilhaIdOrderByOrdem(UUID trilhaId);

    long countByTrilhaId(UUID trilhaId);

    /** Maior ordem ocupada na trilha; 0 quando ela ainda não tem etapas. */
    @Query("select coalesce(max(e.ordem), 0) from TrilhaEtapa e where e.trilha.id = :trilhaId")
    int maiorOrdem(@Param("trilhaId") UUID trilhaId);
}
