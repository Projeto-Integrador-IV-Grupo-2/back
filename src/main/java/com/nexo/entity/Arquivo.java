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
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Arquivo (PDF, DOCX, TXT ou MD) anexado a um conteúdo (issue #54).
 *
 * <p>O binário fica fora do banco. Aqui ficam o nome original — usado só no
 * download, nunca como caminho no disco —, o tipo, o tamanho e a chave gerada
 * no armazenamento. Um arquivo não muda depois de enviado: criado só por
 * {@link #novo} e sem setters.</p>
 */
@Entity
@Table(name = "arquivo")
public class Arquivo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conteudo_id", nullable = false)
    private Conteudo conteudo;

    @Column(name = "nome_original", nullable = false, length = 255)
    private String nomeOriginal;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo", nullable = false, columnDefinition = "tipo_arquivo")
    private TipoArquivo tipo;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "chave_armazenamento", nullable = false, length = 255, unique = true)
    private String chaveArmazenamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enviado_por", nullable = false)
    private Usuario enviadoPor;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    /** Exigido pelo JPA. Para registrar um arquivo, use {@link #novo}. */
    protected Arquivo() {
    }

    /**
     * Factory Method: o arquivo nasce no tenant do conteúdo e com o tipo MIME
     * do formato, para os dois nunca divergirem.
     */
    public static Arquivo novo(Conteudo conteudo, String nomeOriginal, TipoArquivo tipo, long tamanhoBytes,
                               String chaveArmazenamento, Usuario enviadoPor) {
        Arquivo arquivo = new Arquivo();
        arquivo.tenant = conteudo.getTenant();
        arquivo.conteudo = conteudo;
        arquivo.nomeOriginal = nomeOriginal;
        arquivo.tipo = tipo;
        arquivo.mimeType = tipo.getMimeType();
        arquivo.tamanhoBytes = tamanhoBytes;
        arquivo.chaveArmazenamento = chaveArmazenamento;
        arquivo.enviadoPor = enviadoPor;
        return arquivo;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Conteudo getConteudo() {
        return conteudo;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public TipoArquivo getTipo() {
        return tipo;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public String getChaveArmazenamento() {
        return chaveArmazenamento;
    }

    public Usuario getEnviadoPor() {
        return enviadoPor;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Arquivo other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
