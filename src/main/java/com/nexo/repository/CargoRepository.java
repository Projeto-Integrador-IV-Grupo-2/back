package com.nexo.repository;

import com.nexo.entity.Cargo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CargoRepository extends JpaRepository<Cargo, UUID> {

    List<Cargo> findByTenantIdAndAtivoTrueOrderByNome(UUID tenantId);

    Optional<Cargo> findByTenantIdAndNomeIgnoreCase(UUID tenantId, String nome);
}
