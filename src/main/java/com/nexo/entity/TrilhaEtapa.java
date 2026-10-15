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

/**
 * Uma etapa da trilha: um conteúdo numa posição da sequência (issue #40).
 * A ordem é única por trilha ({@code uq_trilha_etapa_ordem}).
 */
@Entity
@Table(name = "trilha_etapa")
public class TrilhaEtapa {

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
    @JoinColumn(name = "conteudo_id", nullable = false)
    private Conteudo conteudo;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descricao", columnDefinition = "text")
    private String descricao;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public TrilhaEtapa() {
    }

    /**
     * Único lugar que monta uma etapa nova: ela já nasce na próxima ordem livre
     * da trilha, calculada por quem chama a partir da maior ordem existente.
     *
     * @param titulo título da etapa; em branco usa o título do conteúdo
     */
    public static TrilhaEtapa nova(Trilha trilha, Conteudo conteudo, String titulo, String descricao,
                                   int proximaOrdem) {
        TrilhaEtapa etapa = new TrilhaEtapa();
        etapa.setTenant(trilha.getTenant());
        etapa.setTrilha(trilha);
        etapa.setConteudo(conteudo);
        etapa.setTitulo(titulo == null || titulo.isBlank() ? conteudo.getTitulo() : titulo.trim());
        etapa.setDescricao(descricao);
        etapa.setOrdem(proximaOrdem);
        return etapa;
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

    public Conteudo getConteudo() {
        return conteudo;
    }

    public void setConteudo(Conteudo conteudo) {
        this.conteudo = conteudo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrilhaEtapa other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
