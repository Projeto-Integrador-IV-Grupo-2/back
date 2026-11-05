package com.nexo.service.email;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Enviador de dev e test: não manda nada para fora, só registra o envio.
 *
 * <p>Guarda os últimos e-mails em memória — é por aqui que os testes leem o
 * link de redefinição. Com uma pasta configurada (dev), grava também cada
 * e-mail num arquivo de texto, para quem roda o projeto abrir o link sem
 * SMTP. Nada vai para o log: o corpo pode ter um token.</p>
 */
public class EnviadorRegistro implements EnviadorDeEmail {

    private static final int GUARDADOS = 50;
    private static final DateTimeFormatter CARIMBO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Deque<EmailMensagem> enviados = new ConcurrentLinkedDeque<>();
    private final Path pasta;
    private final Clock clock;

    public EnviadorRegistro(Path pasta, Clock clock) {
        this.pasta = pasta;
        this.clock = clock;
    }

    @Override
    public void enviar(EmailMensagem mensagem) {
        enviados.addLast(mensagem);
        while (enviados.size() > GUARDADOS) {
            enviados.pollFirst();
        }
        if (pasta != null) {
            gravar(mensagem);
        }
    }

    /** Os e-mails registrados, do mais antigo ao mais novo. */
    public List<EmailMensagem> enviados() {
        return new ArrayList<>(enviados);
    }

    /** Os e-mails registrados para um destinatário, do mais antigo ao mais novo. */
    public List<EmailMensagem> enviadosPara(String para) {
        return enviados.stream().filter(email -> email.para().equalsIgnoreCase(para)).toList();
    }

    public void limpar() {
        enviados.clear();
    }

    private void gravar(EmailMensagem mensagem) {
        String nome = OffsetDateTime.now(clock).format(CARIMBO) + "-" + UUID.randomUUID() + ".txt";
        String texto = "Para: " + mensagem.para() + "\nAssunto: " + mensagem.assunto() + "\n\n" + mensagem.corpo();
        try {
            Files.createDirectories(pasta);
            Files.writeString(pasta.resolve(nome), texto, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Não foi possível gravar o e-mail na pasta de dev", ex);
        }
    }
}
