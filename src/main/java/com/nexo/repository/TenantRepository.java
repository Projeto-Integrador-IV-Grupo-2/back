package com.nexo.repository;

import com.nexo.entity.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySubdominio(String subdominio);

    Optional<Tenant> findBySubdominioIgnoreCase(String subdominio);

    boolean existsBySubdominio(String subdominio);

    boolean existsBySubdominioIgnoreCase(String subdominio);

    java.util.List<Tenant> findAllByOrderByCriadoEmAsc();
}
