package com.nexo.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma troca de mensagens foi gravada numa sessão do assistente (issue #59).
 *
 * <p>Observer: quem grava a mensagem não mexe mais na sessão — publica este
 * evento e o {@link SessaoDoChatListener} atualiza {@code ultima_interacao} e
 * dá título à conversa.</p>
 *
 * <p>Não alimenta o {@code historico_interacao}, de propósito: quem faz isso é
 * o {@link PerguntaRespondidaEvent}, publicado na mesma consulta. Escutar os
 * dois gravaria cada pergunta duas vezes.</p>
 *
 * <p>Não carrega a resposta do assistente. {@code primeiraPergunta} é a
 * pergunta desta troca; a sessão só a usa como título quando ainda não tem
 * um, então o título nasce da primeira pergunta.</p>
 *
 * @param momento instante da última mensagem da troca
 */
public record MensagemRegistradaEvent(UUID tenantId, UUID usuarioId, UUID sessaoId, UUID mensagemId,
                                      String primeiraPergunta, OffsetDateTime momento) {
}
