package com.nexo.repository;

import com.nexo.entity.Notificacao;
import com.nexo.entity.TipoNotificacao;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Notificações. Toda leitura e escrita leva o tenant e o dono. */
public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    Page<Notificacao> findByTenantIdAndUsuarioIdOrderByCriadaEmDescIdDesc(UUID tenantId, UUID usuarioId,
                                                                          Pageable pageable);

    Page<Notificacao> findByTenantIdAndUsuarioIdAndLidaEmIsNullOrderByCriadaEmDescIdDesc(UUID tenantId,
                                                                                         UUID usuarioId,
                                                                                         Pageable pageable);

    long countByTenantIdAndUsuarioIdAndLidaEmIsNull(UUID tenantId, UUID usuarioId);

    /** Notificação do dono; de outra pessoa ou de outro tenant volta vazio (a API responde 404). */
    Optional<Notificacao> findByIdAndTenantIdAndUsuarioId(UUID id, UUID tenantId, UUID usuarioId);

    @Modifying
    @Query("update Notificacao n set n.lidaEm = :agora "
            + "where n.tenant.id = :tenantId and n.usuario.id = :usuarioId and n.lidaEm is null")
    int marcarTodasComoLidas(@Param("tenantId") UUID tenantId, @Param("usuarioId") UUID usuarioId,
                             @Param("agora") OffsetDateTime agora);

    /** Das referências informadas, as que já têm aviso deste tipo — a rotina não repete. */
    @Query("select n.referenciaId from Notificacao n where n.tipo = :tipo and n.referenciaId in :referencias")
    List<UUID> referenciasJaAvisadas(@Param("tipo") TipoNotificacao tipo,
                                     @Param("referencias") Collection<UUID> referencias);

    /** Insere o aviso se outra instância ainda não o gravou para esta atribuição. */
    @Modifying
    @Query(value = """
            INSERT INTO notificacao (tenant_id, usuario_id, tipo, titulo, mensagem, link, referencia_id)
            VALUES (:tenantId, :usuarioId, :tipo, :titulo, :mensagem, :link, :referenciaId)
            ON CONFLICT (usuario_id, tipo, referencia_id)
            WHERE tipo IN ('PRAZO_PROXIMO', 'PRAZO_VENCIDO')
            DO NOTHING
            """, nativeQuery = true)
    int inserirPrazoProximoSeAusente(@Param("tenantId") UUID tenantId,
                                     @Param("usuarioId") UUID usuarioId,
                                     @Param("tipo") String tipo,
                                     @Param("titulo") String titulo,
                                     @Param("mensagem") String mensagem,
                                     @Param("link") String link,
                                     @Param("referenciaId") UUID referenciaId);

    boolean existsByUsuarioIdAndTipoAndReferenciaId(UUID usuarioId, TipoNotificacao tipo, UUID referenciaId);

    /** Limpeza: notificações lidas antes do limite. */
    @Modifying
    @Query("delete from Notificacao n where n.lidaEm is not null and n.lidaEm < :limite")
    int apagarLidasAntesDe(@Param("limite") OffsetDateTime limite);
}
