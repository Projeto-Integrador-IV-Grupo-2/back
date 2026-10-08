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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Ação de um item do PDI. Do tipo {@link TipoAcaoPdi#TRILHA}, aponta para uma
 * trilha atribuída ao colaborador; dos demais tipos, fica só com descrição.
 * {@code conteudo_id} (DER) fica para a próxima sprint.
 */
@Entity
@Table(name = "pdi_acao")
public class PdiAcao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pdi_item_id", nullable = false)
    private PdiItem pdiItem;

    @Column(name = "descricao", columnDefinition = "text")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo", nullable = false, columnDefinition = "tipo_acao_pdi")
    private TipoAcaoPdi tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trilha_id")
    private Trilha trilha;

    @Column(name = "prazo")
    private LocalDate prazo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "status_acao_pdi")
    private StatusAcaoPdi status = StatusAcaoPdi.PENDENTE;

    @Column(name = "concluido_em")
    private OffsetDateTime concluidoEm;

    /** Exigido pelo JPA. Para criar uma ação, use {@link #deTrilha} ou {@link #descritiva}. */
    protected PdiAcao() {
    }

    public static PdiAcao deTrilha(PdiItem item, Trilha trilha, LocalDate prazo, String descricao) {
        PdiAcao acao = nova(item, TipoAcaoPdi.TRILHA, prazo, descricao);
        acao.trilha = trilha;
        return acao;
    }

    public static PdiAcao descritiva(PdiItem item, TipoAcaoPdi tipo, LocalDate prazo, String descricao) {
        return nova(item, tipo, prazo, descricao);
    }

    private static PdiAcao nova(PdiItem item, TipoAcaoPdi tipo, LocalDate prazo, String descricao) {
        PdiAcao acao = new PdiAcao();
        acao.tenant = item.getTenant();
        acao.pdiItem = item;
        acao.tipo = tipo;
        acao.prazo = prazo;
        acao.descricao = descricao;
        acao.status = StatusAcaoPdi.PENDENTE;
        return acao;
    }

    /**
     * Muda o status e mantém {@code concluido_em} coerente: só uma ação
     * concluída tem data de conclusão.
     */
    public void mudarStatus(StatusAcaoPdi novoStatus, OffsetDateTime agora) {
        this.status = novoStatus;
        this.concluidoEm = novoStatus == StatusAcaoPdi.CONCLUIDA ? agora : null;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public PdiItem getPdiItem() {
        return pdiItem;
    }

    public String getDescricao() {
        return descricao;
    }

    public TipoAcaoPdi getTipo() {
        return tipo;
    }

    public Trilha getTrilha() {
        return trilha;
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public StatusAcaoPdi getStatus() {
        return status;
    }

    public OffsetDateTime getConcluidoEm() {
        return concluidoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PdiAcao other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
