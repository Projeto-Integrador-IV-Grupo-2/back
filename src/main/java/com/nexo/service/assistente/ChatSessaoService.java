package com.nexo.service.assistente;

import com.nexo.dto.assistente.SessaoResponse;
import com.nexo.entity.ChatMensagem;
import com.nexo.entity.ChatSessao;
import com.nexo.entity.ContextoChat;
import com.nexo.entity.FonteMensagem;
import com.nexo.entity.PapelMensagem;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessões e mensagens do assistente (base da issue #59).
 *
 * <p>Escopo por tenant <strong>e</strong> por usuário: sessão de outra pessoa
 * — inclusive para o admin — responde 404, como se não existisse.</p>
 *
 * <p>Singleton sem estado.</p>
 */
@Service
public class ChatSessaoService {

    private final ChatSessaoRepository sessaoRepository;
    private final ChatMensagemRepository mensagemRepository;
    private final Clock clock;

    public ChatSessaoService(ChatSessaoRepository sessaoRepository, ChatMensagemRepository mensagemRepository,
                             Clock clock) {
        this.sessaoRepository = sessaoRepository;
        this.mensagemRepository = mensagemRepository;
        this.clock = clock;
    }

    @Transactional
    public SessaoResponse criar(ContextoChat contexto, UsuarioAutenticado usuario) {
        ChatSessao sessao = ChatSessao.nova(usuario.getTenantId(), usuario.getId(), contexto,
                OffsetDateTime.now(clock));
        return SessaoResponse.de(sessaoRepository.save(sessao));
    }

    /** @throws ResourceNotFoundException sessão inexistente, de outro usuário ou de outro tenant */
    @Transactional(readOnly = true)
    public ChatSessao buscarDoUsuario(UUID sessaoId, UsuarioAutenticado usuario) {
        return sessaoRepository.findByIdAndTenantIdAndUsuarioId(sessaoId, usuario.getTenantId(), usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada: " + sessaoId));
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
     * Grava a pergunta e a resposta juntas — ou nenhuma das duas — e atualiza
     * a última interação e o título da sessão.
     *
     * @return a mensagem do assistente
     */
    @Transactional
    public ChatMensagem registrarTroca(UUID sessaoId, String pergunta, OffsetDateTime momentoPergunta,
                                       String resposta, List<FonteMensagem> fontes) {
        ChatSessao sessao = sessaoRepository.findById(sessaoId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada: " + sessaoId));
        OffsetDateTime agora = OffsetDateTime.now(clock);
        // A resposta precisa vir depois da pergunta mesmo com relógio de baixa resolução.
        OffsetDateTime momentoResposta = agora.isAfter(momentoPergunta) ? agora : momentoPergunta.plusNanos(1_000);
        mensagemRepository.save(ChatMensagem.doUsuario(sessaoId, pergunta, momentoPergunta));
        ChatMensagem mensagem = mensagemRepository.save(
                ChatMensagem.doAssistente(sessaoId, resposta, fontes, momentoResposta));
        sessao.registrarInteracao(pergunta, momentoResposta);
        sessaoRepository.save(sessao);
        return mensagem;
    }
}
