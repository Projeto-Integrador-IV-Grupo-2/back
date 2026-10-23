package com.nexo.repository;

import com.nexo.entity.Categoria;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

    /** Listagem padrão: só as ativas. {@code nome} vazio casa com todas. */
    Page<Categoria> findByTenantIdAndAtivoTrueAndNomeContainingIgnoreCase(UUID tenantId, String nome,
                                                                         Pageable pageable);

    Page<Categoria> findByTenantIdAndNomeContainingIgnoreCase(UUID tenantId, String nome, Pageable pageable);

    Optional<Categoria> findByTenantIdAndNomeIgnoreCase(UUID tenantId, String nome);

    /** Categoria do tenant; de outro tenant volta vazio (a API responde 404). */
    Optional<Categoria> findByIdAndTenantId(UUID id, UUID tenantId);
}
