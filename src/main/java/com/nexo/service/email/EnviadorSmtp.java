package com.nexo.service.email;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Envio de verdade, pelo SMTP de {@code spring.mail.*} (produção). */
public class EnviadorSmtp implements EnviadorDeEmail {

    private final JavaMailSender mailSender;
    private final String remetente;

    public EnviadorSmtp(JavaMailSender mailSender, String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    @Override
    public void enviar(EmailMensagem mensagem) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(remetente);
        email.setTo(mensagem.para());
        email.setSubject(mensagem.assunto());
        email.setText(mensagem.corpo());
        mailSender.send(email);
    }
}
