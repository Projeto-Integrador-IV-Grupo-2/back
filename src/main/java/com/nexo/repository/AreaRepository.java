package com.nexo.repository;

import com.nexo.entity.Area;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaRepository extends JpaRepository<Area, UUID> {

    /** Listagem padrão: só as ativas. {@code nome} vazio casa com todas. */
    Page<Area> findByTenantIdAndAtivoTrueAndNomeContainingIgnoreCase(UUID tenantId, String nome, Pageable pageable);

    /** Inclui as inativas — é o que permite ao administrador reativar uma área. */
    Page<Area> findByTenantIdAndNomeContainingIgnoreCase(UUID tenantId, String nome, Pageable pageable);

    Optional<Area> findByTenantIdAndNomeIgnoreCase(UUID tenantId, String nome);
}
