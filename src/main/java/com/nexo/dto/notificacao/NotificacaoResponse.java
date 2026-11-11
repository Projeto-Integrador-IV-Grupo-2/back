package com.nexo.dto.notificacao;

import com.nexo.entity.Notificacao;
import com.nexo.entity.TipoNotificacao;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma notificação do usuário logado. {@code link} é a rota do front para onde
 * o aviso leva.
 */
public record NotificacaoResponse(
        UUID id,
        TipoNotificacao tipo,
        String titulo,
        String mensagem,
        String link,
        boolean lida,
        OffsetDateTime lidaEm,
        OffsetDateTime criadaEm) {

    public static NotificacaoResponse de(Notificacao notificacao) {
        return new NotificacaoResponse(
                notificacao.getId(),
                notificacao.getTipo(),
                notificacao.getTitulo(),
                notificacao.getMensagem(),
                notificacao.getLink(),
                notificacao.isLida(),
                notificacao.getLidaEm(),
                notificacao.getCriadaEm());
    }
}
