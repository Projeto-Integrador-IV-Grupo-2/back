package com.nexo.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistência do rate limit. O bloqueio por usuário serializa reservas entre instâncias da API. */
@Repository
public class PerguntaRateLimitRepository {

    private final JdbcTemplate jdbc;

    public PerguntaRateLimitRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Usa o relógio do PostgreSQL como fonte única entre instâncias da API.
     * clock_timestamp() é avaliado depois da aquisição do bloqueio, ao contrário
     * de CURRENT_TIMESTAMP, que representa o início da transação.
     */
    public OffsetDateTime agoraDoBanco() {
        return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
    }

    /** Cria a linha de sincronização se necessário e mantém o bloqueio até o fim da transação. */
    public void garantirETravar(UUID tenantId, UUID usuarioId) {
        jdbc.update("""
                INSERT INTO assistente_limite_usuario (tenant_id, usuario_id)
                VALUES (?, ?)
                ON CONFLICT (tenant_id, usuario_id) DO NOTHING
                """, tenantId, usuarioId);
        jdbc.queryForObject("""
                SELECT usuario_id
                FROM assistente_limite_usuario
                WHERE tenant_id = ? AND usuario_id = ?
                FOR UPDATE
                """, UUID.class, tenantId, usuarioId);
    }

    public int removerAntigas(UUID tenantId, UUID usuarioId, OffsetDateTime corte) {
        return jdbc.update("""
                DELETE FROM assistente_consulta_limite
                WHERE tenant_id = ? AND usuario_id = ? AND solicitada_em <= ?
                """, tenantId, usuarioId, corte);
    }

    public long contarDesde(UUID tenantId, UUID usuarioId, OffsetDateTime corte) {
        Long total = jdbc.queryForObject("""
                SELECT count(*)
                FROM assistente_consulta_limite
                WHERE tenant_id = ? AND usuario_id = ? AND solicitada_em > ?
                """, Long.class, tenantId, usuarioId, corte);
        return total == null ? 0 : total;
    }

    public OffsetDateTime buscarMaisAntigaDesde(UUID tenantId, UUID usuarioId, OffsetDateTime corte) {
        return jdbc.query("""
                SELECT min(solicitada_em)
                FROM assistente_consulta_limite
                WHERE tenant_id = ? AND usuario_id = ? AND solicitada_em > ?
                """, resultado -> resultado.next()
                        ? resultado.getObject(1, OffsetDateTime.class) : null, tenantId, usuarioId, corte);
    }

    public void registrar(UUID tenantId, UUID usuarioId, OffsetDateTime momento) {
        jdbc.update("""
                INSERT INTO assistente_consulta_limite (tenant_id, usuario_id, solicitada_em)
                VALUES (?, ?, ?)
                """, tenantId, usuarioId, momento);
    }
}
