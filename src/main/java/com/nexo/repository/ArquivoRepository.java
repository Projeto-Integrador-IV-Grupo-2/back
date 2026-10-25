package com.nexo.repository;

import com.nexo.entity.Arquivo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Arquivos anexados aos conteúdos. Toda consulta leva o tenant. */
public interface ArquivoRepository extends JpaRepository<Arquivo, UUID> {

    /** Arquivo do tenant; de outro tenant volta vazio (a API responde 404). */
    Optional<Arquivo> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Arquivos de um conteúdo, na ordem de envio. */
    List<Arquivo> findByTenantIdAndConteudoIdOrderByCriadoEmAsc(UUID tenantId, UUID conteudoId);

    long countByTenantIdAndConteudoId(UUID tenantId, UUID conteudoId);
}
