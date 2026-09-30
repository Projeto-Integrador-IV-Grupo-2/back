package com.nexo.repository;

import com.nexo.entity.Usuario;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository
        extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    Optional<Usuario> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    /** Usado para recusar a inativação de uma área que ainda tem usuários vinculados. */
    boolean existsByAreaId(UUID areaId);

    long countByAreaId(UUID areaId);

    /**
     * Quantos usuários cada área tem, numa consulta só (evita N+1 na listagem).
     * Área sem usuário não aparece no resultado.
     */
    @Query("select u.area.id as areaId, count(u) as total from Usuario u "
            + "where u.area.id in :areaIds group by u.area.id")
    List<TotalPorArea> contarPorArea(@Param("areaIds") Collection<UUID> areaIds);

    interface TotalPorArea {
        UUID getAreaId();

        long getTotal();
    }
}
