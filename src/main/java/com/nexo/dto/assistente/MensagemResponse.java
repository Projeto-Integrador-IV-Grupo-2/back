package com.nexo.dto.assistente;

import com.nexo.entity.ChatMensagem;
import com.nexo.entity.FonteMensagem;
import com.nexo.entity.PapelMensagem;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Uma fala do histórico da conversa.
 *
 * <p>{@code fontes} traz um item por documento citado, na ordem em que os
 * trechos foram usados — a mesma forma de
 * {@link RespostaAssistenteResponse}, para a tela não precisar de dois
 * formatos. Pergunta do usuário vem sempre sem fontes.</p>
 */
public record MensagemResponse(UUID id, PapelMensagem papel, String conteudo, List<FonteResponse> fontes,
                               OffsetDateTime criadoEm) {

    public static MensagemResponse de(ChatMensagem mensagem) {
        return new MensagemResponse(mensagem.getId(), mensagem.getPapel(), mensagem.getConteudo(),
                documentosDistintos(mensagem.getFontes()), mensagem.getCriadoEm());
    }

    private static List<FonteResponse> documentosDistintos(List<FonteMensagem> fontes) {
        if (fontes == null || fontes.isEmpty()) {
            return List.of();
        }
        Map<UUID, FonteResponse> porDocumento = new LinkedHashMap<>();
        fontes.forEach(fonte -> porDocumento.putIfAbsent(fonte.conteudoId(),
                new FonteResponse(fonte.conteudoId(), fonte.titulo())));
        return List.copyOf(porDocumento.values());
    }
}
