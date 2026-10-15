package com.nexo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Registro de que um usuário concluiu uma etapa (issue #40). Um por (etapa, usuário). */
@Entity
@Table(name = "etapa_progresso")
public class EtapaProgresso {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "etapa_id", nullable = false)
    private TrilhaEtapa etapa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "concluido_em", nullable = false, updatable = false)
    private OffsetDateTime concluidoEm;

    public EtapaProgresso() {
    }

    public static EtapaProgresso de(TrilhaEtapa etapa, Usuario usuario) {
        EtapaProgresso progresso = new EtapaProgresso();
        progresso.tenant = etapa.getTenant();
        progresso.etapa = etapa;
        progresso.usuario = usuario;
        return progresso;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public TrilhaEtapa getEtapa() {
        return etapa;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public OffsetDateTime getConcluidoEm() {
        return concluidoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EtapaProgresso other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
