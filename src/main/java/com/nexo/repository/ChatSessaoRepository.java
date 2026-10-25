package com.nexo.repository;

import com.nexo.entity.ChatSessao;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessaoRepository extends JpaRepository<ChatSessao, UUID> {

    /** Sessão só é encontrada pelo próprio dono, no tenant dele. */
    Optional<ChatSessao> findByIdAndTenantIdAndUsuarioId(UUID id, UUID tenantId, UUID usuarioId);

    /**
     * Conversas do usuário, da mais recente para a mais antiga. A ordem está
     * no nome do método (e não no {@code Pageable}) porque a listagem da API
     * não aceita outra: é o índice {@code idx_chat_sessao_usuario_recentes}.
     */
    Page<ChatSessao> findByTenantIdAndUsuarioIdOrderByUltimaInteracaoDesc(UUID tenantId, UUID usuarioId,
                                                                         Pageable pagina);
}
