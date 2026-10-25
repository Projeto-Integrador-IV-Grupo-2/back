package com.nexo.service.assistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.config.IaProperties;
import com.nexo.dto.assistente.RespostaAssistenteResponse;
import com.nexo.entity.ChatMensagem;
import com.nexo.entity.ChatSessao;
import com.nexo.entity.ContextoChat;
import com.nexo.entity.FonteMensagem;
import com.nexo.entity.Perfil;
import com.nexo.event.PerguntaRespondidaEvent;
import com.nexo.exception.IaIndisponivelException;
import com.nexo.exception.LimiteDePerguntasExcedidoException;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.RespostaIa;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.indexacao.BuscaDeTrechosService;
import com.nexo.service.indexacao.TrechoEncontrado;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsultaAssistenteServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");

    @Mock
    private ChatSessaoService chatSessaoService;

    @Mock
    private BuscaDeTrechosService buscaDeTrechosService;

    @Mock
    private ClienteIa clienteIa;

    @Mock
    private ApplicationEventPublisher publisher;

    private ConsultaAssistenteService service;
    private UsuarioAutenticado usuario;
    private ChatSessao sessao;

    @BeforeEach
    void prepara() {
        IaProperties properties = new IaProperties("fake", null, null, null, null, null, null, null, null,
                new IaProperties.Busca(5, 0.6), new IaProperties.Consulta(3, 6), null);
        service = new ConsultaAssistenteService(chatSessaoService, buscaDeTrechosService,
                new MontadorDePromptFactory(List.of(new MontadorDePromptConhecimento())), clienteIa, publisher,
                Clock.fixed(AGORA, ZoneOffset.UTC), properties);
        usuario = new UsuarioAutenticado(UUID.randomUUID(), UUID.randomUUID(), "Pessoa", "p@nexo.com", "{bcrypt}x",
                Perfil.COLABORADOR, true);
        sessao = ChatSessao.nova(usuario.getTenantId(), usuario.getId(), ContextoChat.CONHECIMENTO, null,
                OffsetDateTime.now());
        ReflectionTestUtils.setField(sessao, "id", UUID.randomUUID());
    }

    @Test
    void respondeComOsTrechosEDevolveDocumentosSemRepetir() {
        UUID ferias = UUID.randomUUID();
        UUID beneficios = UUID.randomUUID();
        List<TrechoEncontrado> trechos = List.of(
                new TrechoEncontrado(UUID.randomUUID(), ferias, "Férias", 1, "a", 0.1),
                new TrechoEncontrado(UUID.randomUUID(), beneficios, "Benefícios", 2, "b", 0.2),
                new TrechoEncontrado(UUID.randomUUID(), ferias, "Férias", 3, "c", 0.3));
        dadoSessaoDentroDoLimite();
        when(buscaDeTrechosService.buscar(usuario.getTenantId(), "Quantos dias?", 5)).thenReturn(trechos);
        when(clienteIa.gerarResposta(anyString(), anyList())).thenReturn(new RespostaIa("Trinta dias.", 1, 1));
        dadoQueRegistraATroca("Trinta dias.");

        RespostaAssistenteResponse resposta = service.perguntar(sessao.getId(), "  Quantos dias?  ", usuario);

        assertThat(resposta.resposta().conteudo()).isEqualTo("Trinta dias.");
        assertThat(resposta.pergunta().conteudo()).isEqualTo("Quantos dias?");
        assertThat(resposta.resposta().fontes()).containsExactly("Férias", "Benefícios");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FonteMensagem>> fontes = ArgumentCaptor.forClass(List.class);
        verify(chatSessaoService).registrarTroca(eq(sessao), eq("Quantos dias?"), any(), eq("Trinta dias."),
                fontes.capture());
        assertThat(fontes.getValue()).hasSize(3);
        verify(publisher).publishEvent(any(PerguntaRespondidaEvent.class));
    }

    @Test
    void semTrechoRelevanteRespondeNaoEncontreiSemChamarOModelo() {
        dadoSessaoDentroDoLimite();
        when(buscaDeTrechosService.buscar(any(), anyString(), anyInt())).thenReturn(List.of());
        dadoQueRegistraATroca(ConsultaAssistenteService.RESPOSTA_NAO_ENCONTREI);

        RespostaAssistenteResponse resposta = service.perguntar(sessao.getId(), "Senha do wifi?", usuario);

        assertThat(resposta.resposta().conteudo()).isEqualTo(ConsultaAssistenteService.RESPOSTA_NAO_ENCONTREI);
        assertThat(resposta.resposta().fontes()).isEmpty();
        verify(clienteIa, never()).gerarResposta(anyString(), anyList());
        ArgumentCaptor<PerguntaRespondidaEvent> evento = ArgumentCaptor.forClass(PerguntaRespondidaEvent.class);
        verify(publisher).publishEvent(evento.capture());
        assertThat(evento.getValue().encontrouFontes()).isFalse();
    }

    @Test
    void provedorForaDoArNaoGravaNada() {
        dadoSessaoDentroDoLimite();
        when(buscaDeTrechosService.buscar(any(), anyString(), anyInt()))
                .thenReturn(List.of(new TrechoEncontrado(UUID.randomUUID(), UUID.randomUUID(), "T", 1, "x", 0.1)));
        when(clienteIa.gerarResposta(anyString(), anyList())).thenThrow(new IaIndisponivelException("fora"));

        assertThatThrownBy(() -> service.perguntar(sessao.getId(), "Pergunta", usuario))
                .isInstanceOf(IaIndisponivelException.class);
        verify(chatSessaoService, never()).registrarTroca(any(), any(), any(), any(), any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void acimaDoLimitePorMinutoResponde429SemBuscar() {
        when(chatSessaoService.buscarDoUsuario(sessao.getId(), usuario)).thenReturn(sessao);
        when(chatSessaoService.perguntasDoUsuarioDesde(usuario.getId(),
                OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC).minusSeconds(60))).thenReturn(3L);

        assertThatThrownBy(() -> service.perguntar(sessao.getId(), "Pergunta", usuario))
                .isInstanceOf(LimiteDePerguntasExcedidoException.class);
        verify(buscaDeTrechosService, never()).buscar(any(), any(), anyInt());
    }

    @Test
    void sessaoDeOutroUsuarioResponde404() {
        when(chatSessaoService.buscarDoUsuario(sessao.getId(), usuario))
                .thenThrow(new ResourceNotFoundException("Sessão não encontrada"));

        assertThatThrownBy(() -> service.perguntar(sessao.getId(), "Pergunta", usuario))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(buscaDeTrechosService, never()).buscar(any(), any(), anyInt());
    }

    @Test
    void sessaoDeTrilhasNaoAceitaPergunta() {
        ChatSessao deTrilhas = ChatSessao.nova(usuario.getTenantId(), usuario.getId(), ContextoChat.TRILHAS, null,
                OffsetDateTime.now());
        when(chatSessaoService.buscarDoUsuario(any(), eq(usuario))).thenReturn(deTrilhas);

        assertThatThrownBy(() -> service.perguntar(UUID.randomUUID(), "Pergunta", usuario))
                .isInstanceOf(OperacaoNaoPermitidaException.class);
    }

    private void dadoSessaoDentroDoLimite() {
        when(chatSessaoService.buscarDoUsuario(sessao.getId(), usuario)).thenReturn(sessao);
        when(chatSessaoService.perguntasDoUsuarioDesde(eq(usuario.getId()), any())).thenReturn(0L);
    }

    /** Devolve a troca montada com os próprios argumentos, como o service real faria. */
    private void dadoQueRegistraATroca(String resposta) {
        when(chatSessaoService.registrarTroca(eq(sessao), anyString(), any(), eq(resposta), anyList()))
                .thenAnswer(chamada -> {
                    String pergunta = chamada.getArgument(1);
                    List<FonteMensagem> fontes = chamada.getArgument(4);
                    OffsetDateTime quando = OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC);
                    return new TrocaRegistrada(sessao,
                            comId(ChatMensagem.doUsuario(usuario.getTenantId(), sessao.getId(), pergunta, quando)),
                            comId(ChatMensagem.doAssistente(usuario.getTenantId(), sessao.getId(), resposta, fontes,
                                    quando)));
                });
    }

    private static ChatMensagem comId(ChatMensagem mensagem) {
        ReflectionTestUtils.setField(mensagem, "id", UUID.randomUUID());
        return mensagem;
    }
}
