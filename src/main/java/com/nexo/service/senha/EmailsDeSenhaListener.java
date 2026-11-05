package com.nexo.service.senha;

import com.nexo.config.EmailProperties;
import com.nexo.config.SenhaProperties;
import com.nexo.event.RedefinicaoSenhaSolicitadaEvent;
import com.nexo.event.SenhaRedefinidaEvent;
import com.nexo.repository.UsuarioRepository;
import com.nexo.service.email.EmailMensagem;
import com.nexo.service.email.EnviadorDeEmail;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer dos e-mails de senha (issue #76): o serviço de senha só publica
 * eventos e não sabe que existe e-mail.
 *
 * <p>Depois do commit e em outra thread ({@code @Async}): o pedido de
 * "esqueci minha senha" responde no mesmo tempo, exista a conta ou não, e uma
 * falha do SMTP nunca desfaz a troca de senha. A falha vai para o log sem o
 * link — o token nunca é registrado.</p>
 */
@Component
public class EmailsDeSenhaListener {

    private static final Logger log = LoggerFactory.getLogger(EmailsDeSenhaListener.class);

    private final EnviadorDeEmail enviador;
    private final UsuarioRepository usuarioRepository;
    private final EmailProperties email;
    private final SenhaProperties senha;

    public EmailsDeSenhaListener(EnviadorDeEmail enviador, UsuarioRepository usuarioRepository,
                                 EmailProperties email, SenhaProperties senha) {
        this.enviador = enviador;
        this.usuarioRepository = usuarioRepository;
        this.email = email;
        this.senha = senha;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoSolicitarRedefinicao(RedefinicaoSenhaSolicitadaEvent evento) {
        String link = email.frontUrl() + "/redefinir-senha?token="
                + URLEncoder.encode(evento.token(), StandardCharsets.UTF_8);
        String corpo = "Olá, " + evento.nome() + ".\n\n"
                + "Recebemos um pedido para redefinir a sua senha no Nexo. Para criar uma nova senha, "
                + "abra o link abaixo:\n\n" + link + "\n\n"
                + "O link vale por " + senha.tokenValidade().toMinutes() + " minutos e só pode ser usado uma vez.\n"
                + "Se não foi você, ignore este e-mail: a sua senha continua a mesma.";
        enviar(new EmailMensagem(evento.email(), "Redefinição de senha do Nexo", corpo), evento.usuarioId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRedefinirSenha(SenhaRedefinidaEvent evento) {
        usuarioRepository.findById(evento.usuarioId()).ifPresent(usuario -> enviar(new EmailMensagem(
                usuario.getEmail(),
                "Sua senha do Nexo foi alterada",
                "Olá, " + usuario.getNomeCompleto() + ".\n\n"
                        + "A senha da sua conta no Nexo acabou de ser alterada pelo link de redefinição.\n"
                        + "Se não foi você, procure o administrador da sua empresa imediatamente."),
                evento.usuarioId()));
    }

    private void enviar(EmailMensagem mensagem, java.util.UUID usuarioId) {
        try {
            enviador.enviar(mensagem);
        } catch (RuntimeException ex) {
            log.warn("Falha ao enviar o e-mail \"{}\" ao usuário {}: {}",
                    mensagem.assunto(), usuarioId, ex.getClass().getSimpleName());
        }
    }
}
