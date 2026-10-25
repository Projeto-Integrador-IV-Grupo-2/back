package com.nexo.service.assistente;

import com.nexo.dto.assistente.MensagemResponse;
import com.nexo.dto.assistente.SessaoResponse;
import com.nexo.entity.ChatMensagem;
import com.nexo.entity.ChatSessao;
import com.nexo.entity.ContextoChat;
import com.nexo.entity.FonteMensagem;
import com.nexo.entity.PapelMensagem;
import com.nexo.event.MensagemRegistradaEvent;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.ChatMensagemRepository;
import com.nexo.repository.ChatSessaoRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessões e mensagens do assistente (issue #59).
 *
 * <p>Escopo por tenant <strong>e</strong> por usuário: sessão de outra pessoa
 * — inclusive para o admin — responde 404, como se não existisse. Toda
 * operação parte de {@link #buscarDoUsuario}, então não há caminho que alcance
 * a conversa alheia.</p>
 *
 * <p>Observer: gravar a troca não mexe na sessão. Quem atualiza
 * {@code ultima_interacao} e dá título à conversa é o
 * {@code SessaoDoChatListener}, a partir do {@link MensagemRegistradaEvent}
 * publicado aqui.</p>
 *
 * <p>Singleton sem estado.</p>
 */
@Service
public class ChatSessaoService {

    private final ChatSessaoRepository sessaoRepository;
    private final ChatMensagemRepository mensagemRepository;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public ChatSessaoService(ChatSessaoRepository sessaoRepository, ChatMensagemRepository mensagemRepository,
                             ApplicationEventPublisher publisher, Clock clock) {
        this.sessaoRepository = sessaoRepository;
        this.mensagemRepository = mensagemRepository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** @param titulo opcional — quem abre a conversa pode já dar nome a ela */
    @Transactional
    public SessaoResponse criar(String titulo, ContextoChat contexto, UsuarioAutenticado usuario) {
        ChatSessao sessao = ChatSessao.nova(usuario.getTenantId(), usuario.getId(), contexto, titulo,
                OffsetDateTime.now(clock));
        return SessaoResponse.de(sessaoRepository.save(sessao));
    }

    /** Conversas do usuário autenticado, da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public Page<SessaoResponse> listar(Pageable pagina, UsuarioAutenticado usuario) {
        return sessaoRepository
                .findByTenantIdAndUsuarioIdOrderByUltimaInteracaoDesc(usuario.getTenantId(), usuario.getId(),
                        semOrdenacao(pagina))
                .map(SessaoResponse::de);
    }

    /** @throws ResourceNotFoundException sessão inexistente, de outro usuário ou de outro tenant */
    @Transactional(readOnly = true)
    public ChatSessao buscarDoUsuario(UUID sessaoId, UsuarioAutenticado usuario) {
        return sessaoRepository.findByIdAndTenantIdAndUsuarioId(sessaoId, usuario.getTenantId(), usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada: " + sessaoId));
    }

    /** Histórico completo da conversa, na ordem em que as mensagens foram trocadas. */
    @Transactional(readOnly = true)
    public List<MensagemResponse> mensagens(UUID sessaoId, UsuarioAutenticado usuario) {
        ChatSessao sessao = buscarDoUsuario(sessaoId, usuario);
        return mensagemRepository.findBySessaoIdOrderByCriadoEm(sessao.getId()).stream()
                .map(MensagemResponse::de)
                .toList();
    }

    /** Troca o título que nasceu da primeira pergunta pelo que o usuário escolheu. */
    @Transactional
    public SessaoResponse renomear(UUID sessaoId, String titulo, UsuarioAutenticado usuario) {
        ChatSessao sessao = buscarDoUsuario(sessaoId, usuario);
        sessao.renomear(titulo);
        return SessaoResponse.de(sessaoRepository.save(sessao));
    }

    /** Apaga a conversa e, junto, todas as mensagens dela. */
    @Transactional
    public void apagar(UUID sessaoId, UsuarioAutenticado usuario) {
        ChatSessao sessao = buscarDoUsuario(sessaoId, usuario);
        mensagemRepository.apagarDaSessao(sessao.getId());
        sessaoRepository.delete(sessao);
    }

    /** As últimas {@code limite} mensagens da sessão, em ordem cronológica. */
    @Transactional(readOnly = true)
    public List<ChatMensagem> ultimasMensagens(UUID sessaoId, int limite) {
        if (limite <= 0) {
            return List.of();
        }
        List<ChatMensagem> recentes = new ArrayList<>(
                mensagemRepository.findBySessaoIdOrderByCriadoEmDesc(sessaoId, PageRequest.of(0, limite)));
        Collections.reverse(recentes);
        return recentes;
    }

    @Transactional(readOnly = true)
    public long perguntasDoUsuarioDesde(UUID usuarioId, OffsetDateTime desde) {
        return mensagemRepository.contarDoUsuarioDesde(usuarioId, PapelMensagem.USUARIO, desde);
    }

    /**
     * Grava a pergunta e a resposta juntas — ou nenhuma das duas — e publica
     * {@link MensagemRegistradaEvent}, que atualiza a sessão.
     *
     * @param sessao sessão já conferida como sendo do usuário autenticado
     * @return a troca inteira, com a sessão já atualizada pelo listener
     */
    @Transactional
    public TrocaRegistrada registrarTroca(ChatSessao sessao, String pergunta, OffsetDateTime momentoPergunta,
                                          String resposta, List<FonteMensagem> fontes) {
        UUID sessaoId = sessao.getId();
        OffsetDateTime agora = OffsetDateTime.now(clock);
        // A resposta precisa vir depois da pergunta mesmo com relógio de baixa resolução.
        OffsetDateTime momentoResposta = agora.isAfter(momentoPergunta) ? agora : momentoPergunta.plusNanos(1_000);
        ChatMensagem daPergunta = mensagemRepository.save(
                ChatMensagem.doUsuario(sessao.getTenantId(), sessaoId, pergunta, momentoPergunta));
        ChatMensagem daResposta = mensagemRepository.save(
                ChatMensagem.doAssistente(sessao.getTenantId(), sessaoId, resposta, fontes, momentoResposta));
        publisher.publishEvent(new MensagemRegistradaEvent(sessao.getTenantId(), sessao.getUsuarioId(), sessaoId,
                daResposta.getId(), pergunta, momentoResposta));
        // Relê dentro da mesma transação: é esta instância que o listener
        // acabou de atualizar, então a resposta já sai com o título e o
        // atualizadoEm novos.
        ChatSessao atualizada = sessaoRepository.findById(sessaoId).orElse(sessao);
        return new TrocaRegistrada(atualizada, daPergunta, daResposta);
    }

    /**
     * A ordem da listagem é fixa (mais recente primeiro) e já vem do
     * repositório; um {@code sort} vindo da query não pode trocá-la.
     */
    private static Pageable semOrdenacao(Pageable pagina) {
        return PageRequest.of(pagina.getPageNumber(), pagina.getPageSize());
    }
}
