package com.nexo.repository;

import com.nexo.entity.ChatSessao;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessaoRepository extends JpaRepository<ChatSessao, UUID> {

    /** Sessão só é encontrada pelo próprio dono, no tenant dele. */
    Optional<ChatSessao> findByIdAndTenantIdAndUsuarioId(UUID id, UUID tenantId, UUID usuarioId);
}
