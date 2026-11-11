package com.nexo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Um pedido de "esqueci minha senha", contado para o limite por e-mail e por
 * IP (issue #76). A chave é o hash do e-mail ou do IP — nada em claro.
 */
@Entity
@Table(name = "pedido_redefinicao_senha")
public class PedidoRedefinicaoSenha {

    /** O que a chave identifica. */
    public enum TipoChave {
        EMAIL,
        IP
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_chave", nullable = false, length = 10)
    private TipoChave tipoChave;

    @Column(name = "chave_hash", nullable = false, length = 64)
    private String chaveHash;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    /** Exigido pelo JPA. Para registrar um pedido, use {@link #de}. */
    protected PedidoRedefinicaoSenha() {
    }

    public static PedidoRedefinicaoSenha de(TipoChave tipoChave, String chaveHash, OffsetDateTime criadoEm) {
        PedidoRedefinicaoSenha pedido = new PedidoRedefinicaoSenha();
        pedido.tipoChave = tipoChave;
        pedido.chaveHash = chaveHash;
        pedido.criadoEm = criadoEm;
        return pedido;
    }

    public Long getId() {
        return id;
    }

    public TipoChave getTipoChave() {
        return tipoChave;
    }

    public String getChaveHash() {
        return chaveHash;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }
}
