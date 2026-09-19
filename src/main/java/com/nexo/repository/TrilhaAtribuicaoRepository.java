package com.nexo.repository;

import com.nexo.entity.TrilhaAtribuicao;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrilhaAtribuicaoRepository extends JpaRepository<TrilhaAtribuicao, UUID> {

    Optional<TrilhaAtribuicao> findByTrilhaIdAndUsuarioId(UUID trilhaId, UUID usuarioId);

    boolean existsByTrilhaIdAndUsuarioId(UUID trilhaId, UUID usuarioId);

    List<TrilhaAtribuicao> findByTenantIdAndUsuarioId(UUID tenantId, UUID usuarioId);
}
