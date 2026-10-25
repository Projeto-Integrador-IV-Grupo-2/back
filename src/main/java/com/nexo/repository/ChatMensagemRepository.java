package com.nexo.repository;

import com.nexo.entity.ChatMensagem;
import com.nexo.entity.PapelMensagem;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMensagemRepository extends JpaRepository<ChatMensagem, UUID> {

    /** Mensagens mais recentes primeiro — quem chama limita com {@code Pageable} e inverte. */
    List<ChatMensagem> findBySessaoIdOrderByCriadoEmDesc(UUID sessaoId, Pageable pagina);

    List<ChatMensagem> findBySessaoIdOrderByCriadoEm(UUID sessaoId);

    /** Mensagens de um papel enviadas pelo usuário, em todas as sessões dele, desde um instante. */
    @Query("""
            select count(m)
            from ChatMensagem m
            join ChatSessao s on s.id = m.sessaoId
            where s.usuarioId = :usuarioId
              and m.papel = :papel
              and m.criadoEm >= :desde
            """)
    long contarDoUsuarioDesde(@Param("usuarioId") UUID usuarioId,
                              @Param("papel") PapelMensagem papel,
                              @Param("desde") OffsetDateTime desde);
}
