package com.nexo.service.assistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.assistente.MensagemResponse;
import com.nexo.dto.assistente.SessaoResponse;
import com.nexo.entity.ChatMensagem;
import com.nexo.entity.ChatSessao;
import com.nexo.entity.ContextoChat;
import com.nexo.entity.FonteMensagem;
import com.nexo.entity.PapelMensagem;
import com.nexo.entity.Perfil;
import com.nexo.event.MensagemRegistradaEvent;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.ChatMensagemRepository;
import com.nexo.repository.ChatSessaoRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/** Regras das sessões e do histórico do assistente (issue #59). */
@ExtendWith(MockitoExtension.class)
class ChatSessaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");

    @Mock
    private ChatSessaoRepository sessaoRepository;

    @Mock
    private ChatMensagemRepository mensagemRepository;

    @Mock
    private ApplicationEventPublisher publisher;

    private ChatSessaoService service;
    private UsuarioAutenticado usuario;
    private ChatSessao sessao;

    @BeforeEach
    void prepara() {
        service = new ChatSessaoService(sessaoRepository, mensagemRepository, publisher,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        usuario = new UsuarioAutenticado(UUID.randomUUID(), UUID.randomUUID(), "Pessoa", "p@nexo.com", "{bcrypt}x",
                Perfil.COLABORADOR, true);
        sessao = sessaoCom(usuario.getTenantId(), usuario.getId());
    }

    @Test
    void listaAsConversasDoUsuarioDaMaisRecenteParaAMaisAntiga() {
        ChatSessao antiga = sessaoCom(usuario.getTenantId(), usuario.getId());
        when(sessaoRepository.findByTenantIdAndUsuarioIdOrderByUltimaInteracaoDesc(
                eq(usuario.getTenantId()), eq(usuario.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sessao, antiga)));

        Page<SessaoResponse> pagina = service.listar(PageRequest.of(0, 20), usuario);

        assertThat(pagina.getContent()).extracting(SessaoResponse::id)
                .containsExactly(sessao.getId(), antiga.getId());
    }

    @Test
    void aOrdenacaoPedidaNaQueryNaoTrocaAOrdemDaListagem() {
        when(sessaoRepository.findByTenantIdAndUsuarioIdOrderByUltimaInteracaoDesc(any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listar(PageRequest.of(1, 5, Sort.by("titulo")), usuario);

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(sessaoRepository).findByTenantIdAndUsuarioIdOrderByUltimaInteracaoDesc(any(), any(),
                pagina.capture());
        assertThat(pagina.getValue().getSort().isSorted()).isFalse();
        assertThat(pagina.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pagina.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void devolveAsMensagensNaOrdemEmQueForamTrocadas() {
        UUID conteudoId = UUID.randomUUID();
        ChatMensagem pergunta = ChatMensagem.doUsuario(sessao.getId(), "Quantos dias de férias?", instante(0));
        ChatMensagem resposta = ChatMensagem.doAssistente(sessao.getId(), "Trinta dias.",
                List.of(new FonteMensagem(conteudoId, "Política de férias", UUID.randomUUID(), 1),
                        new FonteMensagem(conteudoId, "Política de férias", UUID.randomUUID(), 2)),
                instante(1));
        dadoQueASessaoEDoUsuario();
        when(mensagemRepository.findBySessaoIdOrderByCriadoEm(sessao.getId()))
                .thenReturn(List.of(pergunta, resposta));

        List<MensagemResponse> mensagens = service.mensagens(sessao.getId(), usuario);

        assertThat(mensagens).extracting(MensagemResponse::papel)
                .containsExactly(PapelMensagem.USUARIO, PapelMensagem.ASSISTENTE);
        assertThat(mensagens.get(0).fontes()).isEmpty();
        // Dois trechos do mesmo documento viram uma fonte só.
        assertThat(mensagens.get(1).fontes()).extracting(fonte -> fonte.conteudoId()).containsExactly(conteudoId);
    }

    @Test
    void sessaoDeOutroUsuarioNaoEntregaMensagens() {
        when(sessaoRepository.findByIdAndTenantIdAndUsuarioId(sessao.getId(), usuario.getTenantId(), usuario.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mensagens(sessao.getId(), usuario))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(mensagemRepository, never()).findBySessaoIdOrderByCriadoEm(any());
    }

    @Test
    void renomeiaNormalizandoEspacosECortandoNoLimiteDaColuna() {
        dadoQueASessaoEDoUsuario();
        when(sessaoRepository.save(sessao)).thenReturn(sessao);

        service.renomear(sessao.getId(), "  Dúvidas   sobre   férias  ", usuario);
        assertThat(sessao.getTitulo()).isEqualTo("Dúvidas sobre férias");

        service.renomear(sessao.getId(), "a".repeat(200), usuario);
        assertThat(sessao.getTitulo()).hasSize(ChatSessao.TAMANHO_MAXIMO_TITULO);
    }

    @Test
    void renomearSessaoDeOutroUsuarioResponde404() {
        when(sessaoRepository.findByIdAndTenantIdAndUsuarioId(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renomear(sessao.getId(), "Outro título", usuario))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(sessaoRepository, never()).save(any());
    }

    @Test
    void apagarASessaoApagaAsMensagensDela() {
        dadoQueASessaoEDoUsuario();

        service.apagar(sessao.getId(), usuario);

        verify(mensagemRepository).apagarDaSessao(sessao.getId());
        verify(sessaoRepository).delete(sessao);
    }

    @Test
    void apagarSessaoDeOutroUsuarioResponde404ENaoApagaNada() {
        when(sessaoRepository.findByIdAndTenantIdAndUsuarioId(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apagar(sessao.getId(), usuario))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(mensagemRepository, never()).apagarDaSessao(any());
        verify(sessaoRepository, never()).delete(any());
    }

    @Test
    void registrarTrocaGravaPerguntaERespostaNaOrdemEAvisaOsObservadores() {
        when(mensagemRepository.save(any(ChatMensagem.class))).thenAnswer(chamada -> {
            ChatMensagem salva = chamada.getArgument(0);
            ReflectionTestUtils.setField(salva, "id", UUID.randomUUID());
            return salva;
        });

        ChatMensagem resposta = service.registrarTroca(sessao, "Quantos dias de férias?", instante(0),
                "Trinta dias.", List.of());

        ArgumentCaptor<ChatMensagem> gravadas = ArgumentCaptor.forClass(ChatMensagem.class);
        verify(mensagemRepository, times(2)).save(gravadas.capture());
        assertThat(gravadas.getAllValues()).extracting(ChatMensagem::getPapel)
                .containsExactly(PapelMensagem.USUARIO, PapelMensagem.ASSISTENTE);
        assertThat(gravadas.getAllValues().get(1).getCriadoEm())
                .isAfter(gravadas.getAllValues().get(0).getCriadoEm());

        ArgumentCaptor<MensagemRegistradaEvent> evento = ArgumentCaptor.forClass(MensagemRegistradaEvent.class);
        verify(publisher).publishEvent(evento.capture());
        assertThat(evento.getValue().sessaoId()).isEqualTo(sessao.getId());
        assertThat(evento.getValue().mensagemId()).isEqualTo(resposta.getId());
        assertThat(evento.getValue().primeiraPergunta()).isEqualTo("Quantos dias de férias?");
        // A sessão só é tocada pelo listener — o service de gravação não mexe nela.
        verify(sessaoRepository, never()).save(any());
    }

    @Test
    void criarSessaoSemContextoNasceEmConhecimentoESemTitulo() {
        when(sessaoRepository.save(any(ChatSessao.class))).thenAnswer(chamada -> chamada.getArgument(0));

        SessaoResponse resposta = service.criar(null, usuario);

        assertThat(resposta.contexto()).isEqualTo(ContextoChat.CONHECIMENTO);
        assertThat(resposta.titulo()).isNull();
    }

    @Test
    void semMensagensNoHistoricoDevolveListaVazia() {
        dadoQueASessaoEDoUsuario();
        when(mensagemRepository.findBySessaoIdOrderByCriadoEm(sessao.getId())).thenReturn(List.of());

        assertThat(service.mensagens(sessao.getId(), usuario)).isEmpty();
    }

    @Test
    void ultimasMensagensVoltamEmOrdemCronologica() {
        ChatMensagem nova = ChatMensagem.doUsuario(sessao.getId(), "nova", instante(2));
        ChatMensagem antiga = ChatMensagem.doUsuario(sessao.getId(), "antiga", instante(1));
        when(mensagemRepository.findBySessaoIdOrderByCriadoEmDesc(eq(sessao.getId()), any(Pageable.class)))
                .thenReturn(List.of(nova, antiga));

        assertThat(service.ultimasMensagens(sessao.getId(), 2))
                .extracting(ChatMensagem::getConteudo)
                .containsExactly("antiga", "nova");
        assertThat(service.ultimasMensagens(sessao.getId(), 0)).isEmpty();
    }

    private void dadoQueASessaoEDoUsuario() {
        when(sessaoRepository.findByIdAndTenantIdAndUsuarioId(sessao.getId(), usuario.getTenantId(), usuario.getId()))
                .thenReturn(Optional.of(sessao));
    }

    private static ChatSessao sessaoCom(UUID tenantId, UUID usuarioId) {
        ChatSessao nova = ChatSessao.nova(tenantId, usuarioId, ContextoChat.CONHECIMENTO,
                OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC));
        ReflectionTestUtils.setField(nova, "id", UUID.randomUUID());
        return nova;
    }

    private static OffsetDateTime instante(int segundos) {
        return OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC).plusSeconds(segundos);
    }
}
