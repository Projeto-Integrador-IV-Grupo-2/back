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
 * Link de "esqueci minha senha" (issue #76). Guarda só o hash do token — o
 * valor só existe no e-mail enviado — e vale até {@code expiraEm}, uma vez.
 */
@Entity
@Table(name = "token_redefinicao_senha")
public class TokenRedefinicaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "usado_em")
    private OffsetDateTime usadoEm;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    /** Exigido pelo JPA. Para criar um token, use {@link #novo}. */
    protected TokenRedefinicaoSenha() {
    }

    /** Factory Method: o token nasce no tenant do usuário, ainda não usado. */
    public static TokenRedefinicaoSenha novo(Usuario usuario, String tokenHash, OffsetDateTime expiraEm) {
        TokenRedefinicaoSenha token = new TokenRedefinicaoSenha();
        token.tenant = usuario.getTenant();
        token.usuario = usuario;
        token.tokenHash = tokenHash;
        token.expiraEm = expiraEm;
        return token;
    }

    /** Ainda não usado e dentro da validade. */
    public boolean valido(OffsetDateTime agora) {
        return usadoEm == null && agora.isBefore(expiraEm);
    }

    public void marcarUsado(OffsetDateTime agora) {
        this.usadoEm = agora;
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

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiraEm() {
        return expiraEm;
    }

    public OffsetDateTime getUsadoEm() {
        return usadoEm;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TokenRedefinicaoSenha other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
