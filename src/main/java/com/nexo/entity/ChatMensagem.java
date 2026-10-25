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
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Uma fala da conversa com o assistente (base da issue #59). */
@Entity
@Table(name = "chat_mensagem")
public class ChatMensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "sessao_id", nullable = false, updatable = false)
    private UUID sessaoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "papel", nullable = false, updatable = false, columnDefinition = "papel_mensagem")
    private PapelMensagem papel;

    @Column(name = "conteudo", nullable = false, updatable = false, columnDefinition = "text")
    private String conteudo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fontes", nullable = false, columnDefinition = "jsonb")
    private List<FonteMensagem> fontes = List.of();

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    protected ChatMensagem() {
    }

    /** Factory Method: pergunta do usuário. */
    public static ChatMensagem doUsuario(UUID tenantId, UUID sessaoId, String pergunta, OffsetDateTime quando) {
        return nova(tenantId, sessaoId, PapelMensagem.USUARIO, pergunta, List.of(), quando);
    }

    /** Factory Method: resposta do assistente, com os trechos usados. */
    public static ChatMensagem doAssistente(UUID tenantId, UUID sessaoId, String resposta,
                                            List<FonteMensagem> fontes, OffsetDateTime quando) {
        return nova(tenantId, sessaoId, PapelMensagem.ASSISTENTE, resposta, fontes, quando);
    }

    private static ChatMensagem nova(UUID tenantId, UUID sessaoId, PapelMensagem papel, String conteudo,
                                     List<FonteMensagem> fontes, OffsetDateTime quando) {
        ChatMensagem mensagem = new ChatMensagem();
        mensagem.tenantId = tenantId;
        mensagem.sessaoId = sessaoId;
        mensagem.papel = papel;
        mensagem.conteudo = conteudo;
        mensagem.fontes = fontes == null ? List.of() : List.copyOf(fontes);
        mensagem.criadoEm = quando;
        return mensagem;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getSessaoId() {
        return sessaoId;
    }

    public PapelMensagem getPapel() {
        return papel;
    }

    public String getConteudo() {
        return conteudo;
    }

    public List<FonteMensagem> getFontes() {
        return fontes;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }
}
