package com.nexo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Progresso de um usuário numa trilha (Card 06).
 *
 * <p>Um usuário tem no máximo uma atribuição por trilha (ver
 * {@code uq_atribuicao_trilha_usuario}). O percentual de conclusão não é um
 * campo aqui — no DER do projeto ele é calculado a partir do progresso por
 * etapa ({@code etapa_progresso}), que depende de {@code trilha_etapa}/
 * {@code conteudo} (outro card, ainda não implementados nesta base).</p>
 */
@Entity
@Table(
    name = "trilha_atribuicao",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_atribuicao_trilha_usuario",
        columnNames = {"trilha_id", "usuario_id"}
    )
)
public class TrilhaAtribuicao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trilha_id", nullable = false)
    private Trilha trilha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atribuido_por", nullable = false)
    private Usuario atribuidoPor;

    @Column(name = "prazo")
    private LocalDate prazo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "status_atribuicao")
    private StatusAtribuicao status = StatusAtribuicao.NAO_INICIADA;

    @CreationTimestamp
    @Column(name = "atribuido_em", nullable = false, updatable = false)
    private OffsetDateTime atribuidoEm;

    @Column(name = "concluido_em")
    private OffsetDateTime concluidoEm;

    public TrilhaAtribuicao() {
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public Trilha getTrilha() {
        return trilha;
    }

    public void setTrilha(Trilha trilha) {
        this.trilha = trilha;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Usuario getAtribuidoPor() {
        return atribuidoPor;
    }

    public void setAtribuidoPor(Usuario atribuidoPor) {
        this.atribuidoPor = atribuidoPor;
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public void setPrazo(LocalDate prazo) {
        this.prazo = prazo;
    }

    public StatusAtribuicao getStatus() {
        return status;
    }

    public void setStatus(StatusAtribuicao status) {
        this.status = status;
    }

    public OffsetDateTime getAtribuidoEm() {
        return atribuidoEm;
    }

    public OffsetDateTime getConcluidoEm() {
        return concluidoEm;
    }

    public void setConcluidoEm(OffsetDateTime concluidoEm) {
        this.concluidoEm = concluidoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrilhaAtribuicao other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
