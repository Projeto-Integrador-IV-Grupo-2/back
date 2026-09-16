package com.nexo.repository;

import com.nexo.entity.Area;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaRepository extends JpaRepository<Area, UUID> {

    List<Area> findByTenantIdAndAtivoTrueOrderByNome(UUID tenantId);

    Optional<Area> findByTenantIdAndNomeIgnoreCase(UUID tenantId, String nome);
}
