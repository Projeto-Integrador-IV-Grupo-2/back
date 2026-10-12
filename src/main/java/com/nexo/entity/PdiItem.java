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
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/** Competência a desenvolver dentro de um PDI, com nível atual e alvo (1 a 5). */
@Entity
@Table(
    name = "pdi_item",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_pdi_item_pdi_competencia",
        columnNames = {"pdi_id", "competencia_id"}
    )
)
public class PdiItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pdi_id", nullable = false)
    private Pdi pdi;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competencia_id", nullable = false)
    private Competencia competencia;

    @Column(name = "nivel_atual", nullable = false)
    private int nivelAtual;

    @Column(name = "nivel_alvo", nullable = false)
    private int nivelAlvo;

    @Column(name = "observacao", columnDefinition = "text")
    private String observacao;

    /** Exigido pelo JPA. Para criar um item, use {@link #novo}. */
    protected PdiItem() {
    }

    public static PdiItem novo(Pdi pdi, Competencia competencia, int nivelAtual, int nivelAlvo, String observacao) {
        PdiItem item = new PdiItem();
        item.tenant = pdi.getTenant();
        item.pdi = pdi;
        item.competencia = competencia;
        item.nivelAtual = nivelAtual;
        item.nivelAlvo = nivelAlvo;
        item.observacao = observacao;
        return item;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Pdi getPdi() {
        return pdi;
    }

    public Competencia getCompetencia() {
        return competencia;
    }

    public int getNivelAtual() {
        return nivelAtual;
    }

    public int getNivelAlvo() {
        return nivelAlvo;
    }

    public String getObservacao() {
        return observacao;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PdiItem other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
