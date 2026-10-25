package com.nexo.dto.assistente;

/**
 * Resposta de {@code POST /api/assistente/sessoes/{id}/mensagens}.
 *
 * <p>Devolve a troca inteira: a sessão já com o {@code atualizadoEm} novo (e
 * com o título, quando ele nasceu desta pergunta), a mensagem do usuário e a
 * do assistente. Assim a tela não precisa recarregar nada depois de
 * perguntar.</p>
 */
public record RespostaAssistenteResponse(SessaoResponse sessao, MensagemResponse pergunta,
                                         MensagemResponse resposta) {
}
