package com.nexo.dto.assistente;

import com.nexo.entity.ChatMensagem;
import com.nexo.entity.FonteMensagem;
import com.nexo.entity.PapelMensagem;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Uma fala da conversa.
 *
 * <p>{@code fontes} são os títulos dos documentos citados, sem repetir e na
 * ordem em que os trechos foram usados — a tela mostra cada um como está.
 * Pergunta do usuário vem sempre com a lista vazia.</p>
 */
public record MensagemResponse(java.util.UUID id, PapelMensagem papel, String conteudo, List<String> fontes,
                               OffsetDateTime criadoEm) {

    public static MensagemResponse de(ChatMensagem mensagem) {
        return new MensagemResponse(mensagem.getId(), mensagem.getPapel(), mensagem.getConteudo(),
                titulosDistintos(mensagem.getFontes()), mensagem.getCriadoEm());
    }

    /** Vários trechos do mesmo documento viram uma fonte só. */
    static List<String> titulosDistintos(List<FonteMensagem> fontes) {
        if (fontes == null || fontes.isEmpty()) {
            return List.of();
        }
        Set<String> titulos = new LinkedHashSet<>();
        fontes.forEach(fonte -> {
            if (fonte.titulo() != null && !fonte.titulo().isBlank()) {
                titulos.add(fonte.titulo());
            }
        });
        return List.copyOf(titulos);
    }
}
