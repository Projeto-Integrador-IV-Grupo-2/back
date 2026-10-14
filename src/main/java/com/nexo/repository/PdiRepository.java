package com.nexo.repository;

import com.nexo.entity.Pdi;
import com.nexo.entity.StatusPdi;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdiRepository extends JpaRepository<Pdi, UUID> {

    boolean existsByTenantIdAndColaboradorIdAndCiclo(UUID tenantId, UUID colaboradorId, String ciclo);

    /** Visão do administrador: todos os PDIs do tenant. */
    Page<Pdi> findByTenantId(UUID tenantId, Pageable pageable);

    /** Visão do gestor: os PDIs em que ele é o gestor. */
    Page<Pdi> findByTenantIdAndGestorId(UUID tenantId, UUID gestorId, Pageable pageable);

    /** Visão do colaborador: só os próprios PDIs. */
    Page<Pdi> findByTenantIdAndColaboradorId(UUID tenantId, UUID colaboradorId, Pageable pageable);

    /** "Meu PDI": o PDI no status pedido mais recente do colaborador. */
    Optional<Pdi> findFirstByTenantIdAndColaboradorIdAndStatusOrderByInicioDescCriadoEmDesc(
            UUID tenantId, UUID colaboradorId, StatusPdi status);
}
