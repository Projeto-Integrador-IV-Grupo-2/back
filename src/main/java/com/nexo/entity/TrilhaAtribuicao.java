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
 * {@code uq_atribuicao_trilha_usuario}). O {@code percentual} é etapas
 * concluídas / total de etapas da trilha, recalculado por
 * {@code RecalculoDeProgressoListener} sempre que o usuário conclui uma etapa
 * ou as etapas da trilha mudam (issue #40).</p>
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

    @Column(name = "percentual", nullable = false)
    private int percentual;

    public TrilhaAtribuicao() {
    }

    /**
     * Único lugar que monta uma atribuição.
     *
     * <p>Existem dois caminhos para uma trilha chegar a um colaborador — ele
     * mesmo iniciando, ou um gestor atribuindo — e os dois precisam preencher
     * tenant, trilha, usuário, quem atribuiu e prazo da mesma forma. Centralizar
     * aqui evita que um dos caminhos esqueça um campo (o {@code atribuido_por},
     * por exemplo, é {@code NOT NULL} no banco).</p>
     *
     * @param trilha        trilha atribuída; dela sai o tenant
     * @param colaborador   quem vai cumprir a trilha
     * @param quemAtribuiu  quem originou a atribuição (o próprio colaborador, quando ele se inicia)
     * @param prazo         data limite; {@code null} quando a trilha não tem prazo
     * @param status        situação inicial
     */
    public static TrilhaAtribuicao nova(Trilha trilha, Usuario colaborador, Usuario quemAtribuiu,
                                        LocalDate prazo, StatusAtribuicao status) {
        TrilhaAtribuicao atribuicao = new TrilhaAtribuicao();
        atribuicao.setTenant(trilha.getTenant());
        atribuicao.setTrilha(trilha);
        atribuicao.setUsuario(colaborador);
        atribuicao.setAtribuidoPor(quemAtribuiu);
        atribuicao.setPrazo(prazo);
        atribuicao.setStatus(status);
        return atribuicao;
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

    public int getPercentual() {
        return percentual;
    }

    public void setPercentual(int percentual) {
        this.percentual = percentual;
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
