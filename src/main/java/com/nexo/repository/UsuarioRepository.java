package com.nexo.repository;

import com.nexo.entity.Perfil;
import com.nexo.entity.Usuario;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository
        extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    List<Usuario> findTop2ByEmailIgnoreCaseOrderByIdAsc(String email);

    /** Contas ativas com o e-mail, em qualquer tenant — "esqueci minha senha" sem empresa informada. */
    List<Usuario> findByEmailIgnoreCaseAndAtivoTrue(String email);

    Optional<Usuario> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    /** Opções de colaborador ao criar um PDI: usuários ativos do tenant. {@code nome} vazio casa com todos. */
    Page<Usuario> findByTenantIdAndAtivoTrueAndNomeCompletoContainingIgnoreCase(UUID tenantId, String nome,
                                                                                Pageable pageable);

    /** Usado para recusar a inativação de uma área que ainda tem usuários vinculados. */
    boolean existsByAreaId(UUID areaId);

    long countByAreaId(UUID areaId);

    /** Destinatários de uma atribuição de trilha por área: só os ativos. */
    List<Usuario> findByTenantIdAndAreaIdAndAtivoTrue(UUID tenantId, UUID areaId);

    /** Usuários ativos de um perfil, por trecho do nome — quem pode receber PDI ou trilha. */
    Page<Usuario> findByTenantIdAndAtivoTrueAndPapelAndNomeCompletoContainingIgnoreCase(
            UUID tenantId, Perfil papel, String nome, Pageable pageable);

    /** Destinatários de notificação por perfil: os ativos de uma área (issue #78). */
    List<Usuario> findByTenantIdAndAreaIdAndPapelAndAtivoTrue(UUID tenantId, UUID areaId, Perfil papel);

    /** Destinatários de notificação por perfil: os ativos do tenant inteiro (issue #78). */
    List<Usuario> findByTenantIdAndPapelAndAtivoTrue(UUID tenantId, Perfil papel);

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
