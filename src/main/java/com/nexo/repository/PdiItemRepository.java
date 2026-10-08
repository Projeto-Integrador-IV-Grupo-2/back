package com.nexo.repository;

import com.nexo.entity.PdiItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdiItemRepository extends JpaRepository<PdiItem, UUID> {

    List<PdiItem> findByPdiIdOrderByCompetenciaNomeAsc(UUID pdiId);

    boolean existsByPdiIdAndCompetenciaId(UUID pdiId, UUID competenciaId);
}
