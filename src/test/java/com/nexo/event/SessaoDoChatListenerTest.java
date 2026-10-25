package com.nexo.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.entity.ChatSessao;
import com.nexo.entity.ContextoChat;
import com.nexo.repository.ChatSessaoRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Observer que mantém a sessão em dia a cada mensagem (issue #59). */
@ExtendWith(MockitoExtension.class)
class SessaoDoChatListenerTest {

    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-10-20T12:00:00Z");

    @Mock
    private ChatSessaoRepository sessaoRepository;

    private SessaoDoChatListener listener;
    private UUID tenantId;
    private UUID usuarioId;
    private ChatSessao sessao;

    @BeforeEach
    void prepara() {
        listener = new SessaoDoChatListener(sessaoRepository);
        tenantId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
        sessao = ChatSessao.nova(tenantId, usuarioId, ContextoChat.CONHECIMENTO, INICIO);
        ReflectionTestUtils.setField(sessao, "id", UUID.randomUUID());
    }

    @Test
    void cadaMensagemNovaEmpurraAUltimaInteracao() {
        dadoQueASessaoExiste();

        listener.aoRegistrarMensagem(evento("Primeira pergunta", INICIO.plusMinutes(1)));
        assertThat(sessao.getUltimaInteracao()).isEqualTo(INICIO.plusMinutes(1));

        listener.aoRegistrarMensagem(evento("Segunda pergunta", INICIO.plusMinutes(5)));
        assertThat(sessao.getUltimaInteracao()).isEqualTo(INICIO.plusMinutes(5));
    }

    @Test
    void oTituloNasceDaPrimeiraPerguntaENaoMudaDepois() {
        dadoQueASessaoExiste();

        listener.aoRegistrarMensagem(evento("Quantos dias de férias eu tenho?", INICIO.plusMinutes(1)));
        listener.aoRegistrarMensagem(evento("E o vale-refeição?", INICIO.plusMinutes(2)));

        assertThat(sessao.getTitulo()).isEqualTo("Quantos dias de férias eu tenho?");
    }

    @Test
    void perguntaLongaViraTituloCortado() {
        dadoQueASessaoExiste();

        listener.aoRegistrarMensagem(evento("a".repeat(200), INICIO.plusMinutes(1)));

        assertThat(sessao.getTitulo()).hasSize(ChatSessao.TAMANHO_TITULO + 1).endsWith("…");
    }

    @Test
    void sessaoApagadaNoMeioDaTrocaNaoQuebraAGravacao() {
        when(sessaoRepository.findByIdAndTenantIdAndUsuarioId(any(), any(), any())).thenReturn(Optional.empty());

        listener.aoRegistrarMensagem(evento("Pergunta", INICIO.plusMinutes(1)));

        verify(sessaoRepository, never()).save(any());
    }

    private void dadoQueASessaoExiste() {
        when(sessaoRepository.findByIdAndTenantIdAndUsuarioId(sessao.getId(), tenantId, usuarioId))
                .thenReturn(Optional.of(sessao));
    }

    private MensagemRegistradaEvent evento(String pergunta, OffsetDateTime momento) {
        return new MensagemRegistradaEvent(tenantId, usuarioId, sessao.getId(), UUID.randomUUID(), pergunta, momento);
    }
}
