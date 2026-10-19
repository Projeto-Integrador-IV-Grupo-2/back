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
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Registro imutável de auditoria de uma interação do usuário com uma trilha. */
@Entity
@Table(name = "historico_interacao")
public class HistoricoInteracao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    /** Conteúdo relacionado, quando a interação se referir diretamente a um conteúdo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conteudo_id", updatable = false)
    private Conteudo conteudo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, updatable = false)
    private TipoInteracaoHistorico tipo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalhes", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> detalhes = Map.of();

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public HistoricoInteracao() {
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

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Conteudo getConteudo() {
        return conteudo;
    }

    public void setConteudo(Conteudo conteudo) {
        this.conteudo = conteudo;
    }

    public TipoInteracaoHistorico getTipo() {
        return tipo;
    }

    public void setTipo(TipoInteracaoHistorico tipo) {
        this.tipo = tipo;
    }

    public Map<String, Object> getDetalhes() {
        return detalhes;
    }

    public void setDetalhes(Map<String, Object> detalhes) {
        this.detalhes = detalhes == null ? Map.of() : Map.copyOf(detalhes);
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }
}
