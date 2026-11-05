package com.nexo.repository;

import com.nexo.entity.TokenRedefinicaoSenha;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRedefinicaoSenhaRepository extends JpaRepository<TokenRedefinicaoSenha, UUID> {

    /**
     * Busca o token travando a linha: dois pedidos com o mesmo link ao mesmo
     * tempo não conseguem usá-lo duas vezes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokenRedefinicaoSenha> findByTokenHash(String tokenHash);

    /** Invalida os links ainda não usados do usuário (pedir um novo ou redefinir). */
    @Modifying
    @Query("delete from TokenRedefinicaoSenha t where t.usuario.id = :usuarioId and t.usadoEm is null")
    int apagarNaoUsadosDoUsuario(@Param("usuarioId") UUID usuarioId);

    /** Limpeza da rotina: tokens que venceram antes do limite (usados ou não). */
    @Modifying
    @Query("delete from TokenRedefinicaoSenha t where t.expiraEm < :limite")
    int apagarExpiradosAntesDe(@Param("limite") OffsetDateTime limite);
}
