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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Conversa de um usuário com o assistente de IA (base da issue #59). */
@Entity
@Table(name = "chat_sessao")
public class ChatSessao {

    /** Caracteres da pergunta que viram o título automático da conversa. */
    public static final int TAMANHO_TITULO = 60;

    /** Limite da coluna {@code titulo} — o que o usuário pode escrever ao renomear. */
    public static final int TAMANHO_MAXIMO_TITULO = 120;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "contexto", nullable = false, columnDefinition = "contexto_chat")
    private ContextoChat contexto;

    @Column(name = "titulo", length = 120)
    private String titulo;

    @Column(name = "iniciada_em", nullable = false, updatable = false)
    private OffsetDateTime iniciadaEm;

    @Column(name = "ultima_interacao", nullable = false)
    private OffsetDateTime ultimaInteracao;

    protected ChatSessao() {
    }

    /** Factory Method: sessão nova, ainda sem título (ele nasce da primeira pergunta). */
    public static ChatSessao nova(UUID tenantId, UUID usuarioId, ContextoChat contexto, OffsetDateTime agora) {
        ChatSessao sessao = new ChatSessao();
        sessao.tenantId = tenantId;
        sessao.usuarioId = usuarioId;
        sessao.contexto = contexto == null ? ContextoChat.CONHECIMENTO : contexto;
        sessao.iniciadaEm = agora;
        sessao.ultimaInteracao = agora;
        return sessao;
    }

    /** Registra uma troca de mensagens: atualiza a última interação e dá título à sessão, se ainda não tiver. */
    public void registrarInteracao(String pergunta, OffsetDateTime quando) {
        ultimaInteracao = quando;
        if (titulo == null && pergunta != null && !pergunta.isBlank()) {
            String limpa = pergunta.strip().replaceAll("\\s+", " ");
            titulo = limpa.length() <= TAMANHO_TITULO ? limpa : limpa.substring(0, TAMANHO_TITULO).strip() + "…";
        }
    }

    /**
     * Renomeia a conversa. O texto é normalizado e cortado no limite da
     * coluna, para o usuário nunca receber 500 por um título comprido.
     */
    public void renomear(String novoTitulo) {
        String limpo = novoTitulo == null ? "" : novoTitulo.strip().replaceAll("\\s+", " ");
        titulo = limpo.length() <= TAMANHO_MAXIMO_TITULO ? limpo : limpo.substring(0, TAMANHO_MAXIMO_TITULO).strip();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public ContextoChat getContexto() {
        return contexto;
    }

    public String getTitulo() {
        return titulo;
    }

    public OffsetDateTime getIniciadaEm() {
        return iniciadaEm;
    }

    public OffsetDateTime getUltimaInteracao() {
        return ultimaInteracao;
    }
}
