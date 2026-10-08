package com.nexo.repository;

import com.nexo.entity.Competencia;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetenciaRepository extends JpaRepository<Competencia, UUID> {

    /** Listagem padrão: só as ativas. {@code nome} vazio casa com todas. */
    Page<Competencia> findByTenantIdAndAtivoTrueAndNomeContainingIgnoreCase(UUID tenantId, String nome,
                                                                            Pageable pageable);

    /** Inclui as inativas — é o que permite reativar uma competência. */
    Page<Competencia> findByTenantIdAndNomeContainingIgnoreCase(UUID tenantId, String nome, Pageable pageable);

    Optional<Competencia> findByTenantIdAndNomeIgnoreCase(UUID tenantId, String nome);
}
