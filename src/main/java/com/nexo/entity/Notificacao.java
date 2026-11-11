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

/**
 * Aviso para um usuário dentro do sistema (issue #78). Criada só por
 * {@link #nova}; depois disso, só muda ao ser lida.
 */
@Entity
@Table(name = "notificacao")
public class Notificacao {

    public static final int TITULO_MAXIMO = 150;
    public static final int MENSAGEM_MAXIMA = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoNotificacao tipo;

    @Column(name = "titulo", nullable = false, length = TITULO_MAXIMO)
    private String titulo;

    @Column(name = "mensagem", nullable = false, length = MENSAGEM_MAXIMA)
    private String mensagem;

    @Column(name = "link", nullable = false, length = 300)
    private String link;

    @Column(name = "referencia_id")
    private UUID referenciaId;

    @Column(name = "lida_em")
    private OffsetDateTime lidaEm;

    @CreationTimestamp
    @Column(name = "criada_em", nullable = false, updatable = false)
    private OffsetDateTime criadaEm;

    /** Exigido pelo JPA. Para criar uma notificação, use {@link #nova}. */
    protected Notificacao() {
    }

    /**
     * Notificação para o destinatário, no tenant dele. Título e mensagem são
     * cortados no tamanho da coluna — um nome de trilha longo nunca derruba a
     * gravação.
     */
    public static Notificacao nova(Usuario destinatario, TipoNotificacao tipo, String titulo, String mensagem,
                                   String link, UUID referenciaId) {
        Notificacao notificacao = new Notificacao();
        notificacao.tenant = destinatario.getTenant();
        notificacao.usuario = destinatario;
        notificacao.tipo = tipo;
        notificacao.titulo = cortar(titulo, TITULO_MAXIMO);
        notificacao.mensagem = cortar(mensagem, MENSAGEM_MAXIMA);
        notificacao.link = link;
        notificacao.referenciaId = referenciaId;
        return notificacao;
    }

    /** Marca como lida agora; já lida, mantém a data de antes. */
    public void marcarLida(OffsetDateTime agora) {
        if (lidaEm == null) {
            lidaEm = agora;
        }
    }

    public boolean isLida() {
        return lidaEm != null;
    }

    private static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public TipoNotificacao getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getLink() {
        return link;
    }

    public UUID getReferenciaId() {
        return referenciaId;
    }

    public OffsetDateTime getLidaEm() {
        return lidaEm;
    }

    public OffsetDateTime getCriadaEm() {
        return criadaEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notificacao other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
