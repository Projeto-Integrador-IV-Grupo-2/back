package com.nexo.repository;

import com.nexo.entity.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UsuarioRepository
        extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    Optional<Usuario> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    /** Usado para recusar a inativação de uma área que ainda tem usuários vinculados. */
    boolean existsByAreaId(UUID areaId);
}
