package com.nexo.repository;

import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.TrilhaAtribuicao;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrilhaAtribuicaoRepository extends JpaRepository<TrilhaAtribuicao, UUID> {

    Optional<TrilhaAtribuicao> findByTrilhaIdAndUsuarioId(UUID trilhaId, UUID usuarioId);

    boolean existsByTrilhaIdAndUsuarioId(UUID trilhaId, UUID usuarioId);

    List<TrilhaAtribuicao> findByTenantIdAndUsuarioId(UUID tenantId, UUID usuarioId);

    /**
     * Atribuições com prazo vencido que ainda não foram concluídas nem marcadas
     * — a entrada da rotina diária de atraso. Deixar {@code ATRASADA} fora do
     * filtro é o que torna a rotina idempotente no mesmo dia.
     */
    List<TrilhaAtribuicao> findByPrazoBeforeAndStatusNotIn(LocalDate prazo, Collection<StatusAtribuicao> status);
}
