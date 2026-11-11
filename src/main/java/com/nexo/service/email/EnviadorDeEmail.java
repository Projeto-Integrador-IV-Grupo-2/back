package com.nexo.service.email;

/**
 * Envia um e-mail. Quem usa não sabe se é SMTP de verdade ou o registro de
 * dev/test — quem escolhe é a {@link EnviadorDeEmailFactory}.
 */
public interface EnviadorDeEmail {

    void enviar(EmailMensagem mensagem);
}
