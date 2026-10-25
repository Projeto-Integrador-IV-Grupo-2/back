package com.nexo.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma troca de mensagens foi gravada numa sessão do assistente (issue #59).
 *
 * <p>Observer: quem grava a mensagem não mexe mais na sessão — publica este
 * evento e o {@link SessaoDoChatListener} atualiza {@code ultima_interacao} e
 * dá título à conversa. O mesmo gancho serve para alimentar o
 * {@code historico_interacao} sem que o chat precise conhecer a auditoria.</p>
 *
 * <p>Não carrega a resposta do assistente; {@code primeiraPergunta} existe só
 * para o título nascer do começo da primeira pergunta da sessão.</p>
 *
 * @param momento instante da última mensagem da troca
 */
public record MensagemRegistradaEvent(UUID tenantId, UUID usuarioId, UUID sessaoId, UUID mensagemId,
                                      String primeiraPergunta, OffsetDateTime momento) {
}
