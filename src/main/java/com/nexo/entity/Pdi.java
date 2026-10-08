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
 * Plano de Desenvolvimento Individual de um colaborador num ciclo.
 *
 * <p>Criado só por {@link #novo}: o PDI já nasce {@link StatusPdi#ATIVO} e com
 * quem o criou como gestor — não existe PDI sem gestor nem sem status.</p>
 */
@Entity
@Table(
    name = "pdi",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_pdi_tenant_colaborador_ciclo",
        columnNames = {"tenant_id", "colaborador_id", "ciclo"}
    )
)
public class Pdi {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colaborador_id", nullable = false)
    private Usuario colaborador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gestor_id", nullable = false)
    private Usuario gestor;

    @Column(name = "ciclo", nullable = false, length = 20)
    private String ciclo;

    @Column(name = "inicio", nullable = false)
    private LocalDate inicio;

    @Column(name = "fim", nullable = false)
    private LocalDate fim;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "status_pdi")
    private StatusPdi status;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    /** Exigido pelo JPA. Para criar um PDI, use {@link #novo}. */
    protected Pdi() {
    }

    /** Factory Method: o PDI nasce ativo, com quem o criou como gestor. */
    public static Pdi novo(Usuario colaborador, Usuario gestor, String ciclo, LocalDate inicio, LocalDate fim) {
        Pdi pdi = new Pdi();
        pdi.tenant = colaborador.getTenant();
        pdi.colaborador = colaborador;
        pdi.gestor = gestor;
        pdi.ciclo = ciclo;
        pdi.inicio = inicio;
        pdi.fim = fim;
        pdi.status = StatusPdi.ATIVO;
        return pdi;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Usuario getColaborador() {
        return colaborador;
    }

    public Usuario getGestor() {
        return gestor;
    }

    public String getCiclo() {
        return ciclo;
    }

    public LocalDate getInicio() {
        return inicio;
    }

    public LocalDate getFim() {
        return fim;
    }

    public StatusPdi getStatus() {
        return status;
    }

    public void setStatus(StatusPdi status) {
        this.status = status;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pdi other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
