package com.nexo.repository;

import com.nexo.entity.Trilha;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrilhaRepository extends JpaRepository<Trilha, UUID> {

    List<Trilha> findByTenantIdAndAtivoTrueOrderByNome(UUID tenantId);
}
