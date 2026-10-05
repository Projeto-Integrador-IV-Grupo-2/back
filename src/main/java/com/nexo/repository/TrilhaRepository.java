package com.nexo.repository;

import com.nexo.entity.Trilha;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TrilhaRepository extends JpaRepository<Trilha, UUID>, JpaSpecificationExecutor<Trilha> {

    List<Trilha> findByTenantIdAndAtivoTrueOrderByNome(UUID tenantId);

    /**
     * Versão paginada da listagem: a ordenação vem do {@link Pageable}, e não
     * no nome do método, para o cliente poder escolher o critério.
     */
    Page<Trilha> findByTenantIdAndAtivoTrue(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndNomeIgnoreCase(UUID tenantId, String nome);
}
