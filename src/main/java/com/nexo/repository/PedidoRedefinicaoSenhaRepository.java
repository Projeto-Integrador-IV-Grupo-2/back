package com.nexo.repository;

import com.nexo.entity.PedidoRedefinicaoSenha;
import com.nexo.entity.PedidoRedefinicaoSenha.TipoChave;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRedefinicaoSenhaRepository extends JpaRepository<PedidoRedefinicaoSenha, Long> {

    long countByTipoChaveAndChaveHashAndCriadoEmAfter(TipoChave tipoChave, String chaveHash, OffsetDateTime desde);

    /**
     * Trava transacional por chave ({@code pg_advisory_xact_lock}): pedidos
     * simultâneos da mesma chave são contados um de cada vez, e o limite não é
     * furado por concorrência. A trava se solta sozinha no fim da transação.
     */
    @Query(value = "select cast(pg_advisory_xact_lock(hashtext(:chave)) as text)", nativeQuery = true)
    String travar(@Param("chave") String chave);

    /** Limpeza da rotina: pedidos que já saíram de qualquer janela de limite. */
    @Modifying
    @Query("delete from PedidoRedefinicaoSenha p where p.criadoEm < :limite")
    int apagarAntesDe(@Param("limite") OffsetDateTime limite);
}
