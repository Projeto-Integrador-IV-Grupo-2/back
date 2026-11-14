package com.nexo.service.senha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.config.EmailProperties;
import com.nexo.config.SenhaProperties;
import com.nexo.entity.Usuario;
import com.nexo.event.RedefinicaoSenhaSolicitadaEvent;
import com.nexo.event.SenhaTrocadaEvent;
import com.nexo.repository.UsuarioRepository;
import com.nexo.service.email.EmailMensagem;
import com.nexo.service.email.EnviadorDeEmail;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailsDeSenhaListenerTest {

    @Test
    void emailDeRedefinicaoIdentificaAEmpresaDaConta() {
        EnviadorDeEmail enviador = mock(EnviadorDeEmail.class);
        EmailsDeSenhaListener listener = new EmailsDeSenhaListener(
                enviador,
                mock(UsuarioRepository.class),
                new EmailProperties("registro", "nao-responda@nexo.com", "https://nexo.example", null),
                new SenhaProperties(Duration.ofMinutes(30), 3, Duration.ofMinutes(15)));

        listener.aoSolicitarRedefinicao(new RedefinicaoSenhaSolicitadaEvent(
                UUID.randomUUID(), "ana@example.com", "Ana", "Empresa Exemplo", "segredo"));

        ArgumentCaptor<EmailMensagem> mensagem = ArgumentCaptor.forClass(EmailMensagem.class);
        verify(enviador).enviar(mensagem.capture());
        assertThat(mensagem.getValue().corpo()).contains("conta de Empresa Exemplo");
        assertThat(mensagem.getValue().corpo()).contains("https://nexo.example/redefinir-senha?token=segredo");
    }

    @Test
    void trocaPeloPerfilTambemAvisaPorEmail() {
        EnviadorDeEmail enviador = mock(EnviadorDeEmail.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario ana = new Usuario();
        ana.setEmail("ana@example.com");
        ana.setNomeCompleto("Ana");
        UUID id = UUID.randomUUID();
        when(usuarios.findById(id)).thenReturn(Optional.of(ana));
        EmailsDeSenhaListener listener = new EmailsDeSenhaListener(enviador, usuarios,
                new EmailProperties("registro", "nao-responda@nexo.com", "https://nexo.example", null),
                new SenhaProperties(Duration.ofMinutes(30), 3, Duration.ofMinutes(15)));

        listener.aoTrocarSenha(new SenhaTrocadaEvent(UUID.randomUUID(), id));

        ArgumentCaptor<EmailMensagem> mensagem = ArgumentCaptor.forClass(EmailMensagem.class);
        verify(enviador).enviar(mensagem.capture());
        assertThat(mensagem.getValue().para()).isEqualTo("ana@example.com");
        assertThat(mensagem.getValue().corpo()).contains("na tela de perfil");
    }
}
