package com.nexo.service.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nexo.config.EmailProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EnviadorDeEmailTest {

    @TempDir
    Path pasta;

    @Test
    void factoryEscolheOEnviadorPelaConfiguracao() {
        JavaMailSender mailSender = mock(JavaMailSender.class);

        assertThat(EnviadorDeEmailFactory.criar(propriedades("smtp", null), mailSender, Clock.systemUTC()))
                .isInstanceOf(EnviadorSmtp.class);
        assertThat(EnviadorDeEmailFactory.criar(propriedades("registro", null), null, Clock.systemUTC()))
                .isInstanceOf(EnviadorRegistro.class);
        assertThatThrownBy(() -> EnviadorDeEmailFactory.criar(propriedades("pombo", null), null, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnviadorDeEmailFactory.criar(propriedades("smtp", null), null, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void smtpMontaOEmailComORemetenteConfigurado() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EnviadorDeEmail enviador = EnviadorDeEmailFactory.criar(propriedades("smtp", null), mailSender,
                Clock.systemUTC());

        enviador.enviar(new EmailMensagem("ana@empresa.com", "Assunto", "Corpo"));

        ArgumentCaptor<SimpleMailMessage> email = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(email.capture());
        assertThat(email.getValue().getFrom()).isEqualTo("nao-responda@nexo.com.br");
        assertThat(email.getValue().getTo()).containsExactly("ana@empresa.com");
        assertThat(email.getValue().getSubject()).isEqualTo("Assunto");
        assertThat(email.getValue().getText()).isEqualTo("Corpo");
    }

    @Test
    void registroGuardaEmMemoriaEGravaNaPastaQuandoConfigurada() throws Exception {
        EnviadorRegistro enviador = (EnviadorRegistro) EnviadorDeEmailFactory.criar(
                propriedades("registro", pasta.toString()), null, Clock.systemUTC());

        enviador.enviar(new EmailMensagem("ana@empresa.com", "Redefinição", "abra o link"));

        assertThat(enviador.enviadosPara("ANA@empresa.com")).extracting(EmailMensagem::assunto)
                .containsExactly("Redefinição");
        List<Path> arquivos;
        try (var lista = Files.list(pasta)) {
            arquivos = lista.toList();
        }
        assertThat(arquivos).singleElement()
                .satisfies(arquivo -> assertThat(Files.readString(arquivo, StandardCharsets.UTF_8))
                        .contains("Para: ana@empresa.com", "abra o link"));
    }

    @Test
    void registroGuardaSoOsUltimos() {
        EnviadorRegistro enviador = new EnviadorRegistro(null, Clock.systemUTC());

        for (int i = 0; i < 60; i++) {
            enviador.enviar(new EmailMensagem("x@y.com", "e" + i, ""));
        }

        assertThat(enviador.enviados()).hasSize(50);
        assertThat(enviador.enviados().get(49).assunto()).isEqualTo("e59");
    }

    @Test
    void toStringDaMensagemNaoMostraOCorpo() {
        assertThat(new EmailMensagem("a@b.com", "Assunto", "token-secreto").toString())
                .doesNotContain("token-secreto");
    }

    private static EmailProperties propriedades(String enviador, String pastaDosEmails) {
        return new EmailProperties(enviador, null, null, pastaDosEmails);
    }
}
