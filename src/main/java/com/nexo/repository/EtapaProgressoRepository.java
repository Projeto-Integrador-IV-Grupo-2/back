package com.nexo.repository;

import com.nexo.entity.EtapaProgresso;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EtapaProgressoRepository extends JpaRepository<EtapaProgresso, UUID> {

    boolean existsByEtapaIdAndUsuarioId(UUID etapaId, UUID usuarioId);

    /** Etapas da trilha que o usuário já concluiu. */
    @Query("""
            select p.etapa.id from EtapaProgresso p
            where p.etapa.trilha.id = :trilhaId and p.usuario.id = :usuarioId
            """)
    List<UUID> etapasConcluidas(@Param("trilhaId") UUID trilhaId, @Param("usuarioId") UUID usuarioId);

    @Query("""
            select count(p) from EtapaProgresso p
            where p.etapa.trilha.id = :trilhaId and p.usuario.id = :usuarioId
            """)
    long contarConcluidas(@Param("trilhaId") UUID trilhaId, @Param("usuarioId") UUID usuarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EtapaProgresso p where p.etapa.id = :etapaId")
    int removerDaEtapa(@Param("etapaId") UUID etapaId);
}
