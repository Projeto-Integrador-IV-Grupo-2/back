package com.nexo.ia;

/** Uma fala da conversa enviada ao modelo. */
public record MensagemIa(PapelIa papel, String texto) {

    public static MensagemIa doUsuario(String texto) {
        return new MensagemIa(PapelIa.USUARIO, texto);
    }

    public static MensagemIa doAssistente(String texto) {
        return new MensagemIa(PapelIa.ASSISTENTE, texto);
    }
}
