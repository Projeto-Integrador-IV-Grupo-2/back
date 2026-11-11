package com.nexo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Envio de e-mails, lido de {@code app.email.*}.
 *
 * @param enviador  {@code smtp} (envia de verdade) ou {@code registro} (só guarda o envio — dev e test)
 * @param remetente endereço do "De:" ({@code MAIL_REMETENTE})
 * @param frontUrl  endereço do front, base dos links dos e-mails ({@code FRONT_URL})
 * @param pasta     no enviador {@code registro}, pasta onde cada e-mail vira um arquivo; vazio = só memória
 */
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(String enviador, String remetente, String frontUrl, String pasta) {

    public EmailProperties {
        enviador = textoOu(enviador, "smtp");
        remetente = textoOu(remetente, "nao-responda@nexo.com.br");
        frontUrl = textoOu(frontUrl, "http://localhost:5173").replaceAll("/+$", "");
        pasta = pasta == null || pasta.isBlank() ? null : pasta.trim();
    }

    private static String textoOu(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
