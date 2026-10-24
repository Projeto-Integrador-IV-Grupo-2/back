package com.nexo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Trecho de um conteúdo publicado, com o embedding usado na busca do
 * assistente de IA (issue #58).
 *
 * <p>Tenant e conteúdo ficam como ids simples: a indexação roda fora da
 * requisição e nunca precisa navegar até as entidades.</p>
 */
@Entity
@Table(name = "conteudo_chunk")
public class ConteudoChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "conteudo_id", nullable = false, updatable = false)
    private UUID conteudoId;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    @Column(name = "texto", nullable = false, columnDefinition = "text")
    private String texto;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 768)
    @Column(name = "embedding", nullable = false)
    private float[] embedding;

    @Column(name = "indexado_em", nullable = false)
    private OffsetDateTime indexadoEm;

    protected ConteudoChunk() {
    }

    public static ConteudoChunk de(UUID tenantId, UUID conteudoId, int ordem, String texto,
                                   float[] embedding, OffsetDateTime indexadoEm) {
        ConteudoChunk chunk = new ConteudoChunk();
        chunk.tenantId = tenantId;
        chunk.conteudoId = conteudoId;
        chunk.ordem = ordem;
        chunk.texto = texto;
        chunk.embedding = embedding;
        chunk.indexadoEm = indexadoEm;
        return chunk;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getConteudoId() {
        return conteudoId;
    }

    public int getOrdem() {
        return ordem;
    }

    public String getTexto() {
        return texto;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public OffsetDateTime getIndexadoEm() {
        return indexadoEm;
    }
}
