package com.nexo.service.email;

import com.nexo.config.EmailProperties;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Factory Method do enviador de e-mail: escolhe a implementação por
 * {@code app.email.enviador}. SMTP de verdade em produção; em dev e test, o
 * {@link EnviadorRegistro}, que só registra o envio. Trocar de provedor é
 * outra implementação e mais um caso aqui — quem usa não muda.
 */
public final class EnviadorDeEmailFactory {

    private EnviadorDeEmailFactory() {
    }

    public static EnviadorDeEmail criar(EmailProperties properties, JavaMailSender mailSender, Clock clock) {
        String tipo = properties.enviador().toLowerCase(Locale.ROOT);
        return switch (tipo) {
            case "smtp" -> {
                if (mailSender == null) {
                    throw new IllegalStateException("app.email.enviador=smtp exige spring.mail.host (MAIL_HOST)");
                }
                yield new EnviadorSmtp(mailSender, properties.remetente());
            }
            case "registro" -> new EnviadorRegistro(
                    properties.pasta() == null ? null : Path.of(properties.pasta()), clock);
            default -> throw new IllegalStateException(
                    "app.email.enviador desconhecido: '" + properties.enviador() + "' (use smtp ou registro)");
        };
    }
}
