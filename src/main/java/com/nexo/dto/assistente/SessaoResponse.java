package com.nexo.dto.assistente;

import com.nexo.entity.ChatSessao;
import com.nexo.entity.ContextoChat;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Conversa com o assistente.
 *
 * <p>Os nomes {@code criadoEm} e {@code atualizadoEm} seguem o contrato que a
 * tela de histórico já consome, e não os das colunas
 * ({@code iniciado_em}/{@code ultima_interacao}) — foi uma escolha do grupo,
 * registrada no README.</p>
 *
 * @param titulo        nunca nulo: até a primeira pergunta dar nome à
 *                      conversa, vale {@link #TITULO_PADRAO}
 * @param atualizadoEm  muda a cada mensagem nova; é o que ordena o histórico
 */
public record SessaoResponse(UUID id, ContextoChat contexto, String titulo, OffsetDateTime criadoEm,
                             OffsetDateTime atualizadoEm) {

    /** Mostrado enquanto a conversa não tem título próprio. */
    public static final String TITULO_PADRAO = "Nova conversa";

    public static SessaoResponse de(ChatSessao sessao) {
        return new SessaoResponse(sessao.getId(), sessao.getContexto(),
                sessao.getTitulo() == null ? TITULO_PADRAO : sessao.getTitulo(),
                sessao.getIniciadoEm(), sessao.getUltimaInteracao());
    }
}
