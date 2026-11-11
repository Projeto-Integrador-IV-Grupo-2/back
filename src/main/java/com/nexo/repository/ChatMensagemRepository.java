package com.nexo.repository;

import com.nexo.entity.ChatMensagem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMensagemRepository extends JpaRepository<ChatMensagem, UUID> {

    /** Mensagens mais recentes primeiro — quem chama limita com {@code Pageable} e inverte. */
    List<ChatMensagem> findBySessaoIdOrderByCriadoEmDesc(UUID sessaoId, Pageable pagina);

    List<ChatMensagem> findBySessaoIdOrderByCriadoEm(UUID sessaoId);

    /**
     * Apaga as mensagens da sessão antes de a sessão sair. O banco também tem
     * {@code ON DELETE CASCADE}, mas apagar aqui mantém o contexto de
     * persistência coerente em vez de depender só da cascata do Postgres.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ChatMensagem m where m.sessaoId = :sessaoId")
    int apagarDaSessao(@Param("sessaoId") UUID sessaoId);
}
