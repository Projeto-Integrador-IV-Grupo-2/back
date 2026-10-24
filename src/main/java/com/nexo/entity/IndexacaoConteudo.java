package com.nexo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Situação da indexação de um conteúdo (issue #58): guarda a falha por
 * documento e quando tentar de novo.
 */
@Entity
@Table(name = "indexacao_conteudo")
public class IndexacaoConteudo {

    static final int TAMANHO_ERRO = 500;

    @Id
    @Column(name = "conteudo_id")
    private UUID conteudoId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "status_indexacao")
    private StatusIndexacao status = StatusIndexacao.PENDENTE;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", length = TAMANHO_ERRO)
    private String ultimoErro;

    @Column(name = "proxima_tentativa_em")
    private OffsetDateTime proximaTentativaEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected IndexacaoConteudo() {
    }

    public static IndexacaoConteudo de(UUID conteudoId, UUID tenantId, OffsetDateTime agora) {
        IndexacaoConteudo indexacao = new IndexacaoConteudo();
        indexacao.conteudoId = conteudoId;
        indexacao.tenantId = tenantId;
        indexacao.atualizadoEm = agora;
        return indexacao;
    }

    /** Indexação concluída: zera o histórico de falhas. */
    public void marcarIndexado(OffsetDateTime agora) {
        status = StatusIndexacao.INDEXADO;
        tentativas = 0;
        ultimoErro = null;
        proximaTentativaEm = null;
        atualizadoEm = agora;
    }

    /** Registra a falha e agenda a próxima tentativa (ou nenhuma, quando {@code proxima} é nula). */
    public void marcarFalha(String erro, OffsetDateTime agora, OffsetDateTime proxima) {
        status = StatusIndexacao.FALHOU;
        tentativas++;
        ultimoErro = erro == null ? null : erro.length() > TAMANHO_ERRO ? erro.substring(0, TAMANHO_ERRO) : erro;
        proximaTentativaEm = proxima;
        atualizadoEm = agora;
    }

    public UUID getConteudoId() {
        return conteudoId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public StatusIndexacao getStatus() {
        return status;
    }

    public int getTentativas() {
        return tentativas;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }

    public OffsetDateTime getProximaTentativaEm() {
        return proximaTentativaEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
