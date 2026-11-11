package com.nexo.service.email;

/**
 * Um e-mail de texto simples.
 *
 * <p>O {@link #toString()} não mostra o corpo: ele pode levar um link de
 * redefinição de senha, que nunca pode aparecer num log.</p>
 */
public record EmailMensagem(String para, String assunto, String corpo) {

    @Override
    public String toString() {
        return "EmailMensagem[assunto=" + assunto + "]";
    }
}
