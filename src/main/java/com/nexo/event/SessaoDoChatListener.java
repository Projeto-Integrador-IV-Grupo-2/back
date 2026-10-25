package com.nexo.event;

import com.nexo.entity.ChatSessao;
import com.nexo.repository.ChatSessaoRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer das mensagens do assistente (issue #59): mantém
 * {@code ultima_interacao} em dia e dá título à conversa.
 *
 * <p>Síncrono e na <strong>mesma transação</strong> de quem gravou a mensagem,
 * de propósito: a resposta da API já sai com a sessão atualizada, e uma
 * mensagem gravada nunca fica com a sessão parada no tempo. A regra do título
 * e do carimbo vive na entidade — aqui só se escolhe quando aplicá-la.</p>
 */
@Component
public class SessaoDoChatListener {

    private final ChatSessaoRepository sessaoRepository;

    public SessaoDoChatListener(ChatSessaoRepository sessaoRepository) {
        this.sessaoRepository = sessaoRepository;
    }

    @EventListener
    public void aoRegistrarMensagem(MensagemRegistradaEvent evento) {
        ChatSessao sessao = sessaoRepository
                .findByIdAndTenantIdAndUsuarioId(evento.sessaoId(), evento.tenantId(), evento.usuarioId())
                .orElse(null);
        if (sessao == null) {
            // Sessão apagada no meio da troca: não há o que atualizar.
            return;
        }
        sessao.registrarInteracao(evento.primeiraPergunta(), evento.momento());
        sessaoRepository.save(sessao);
    }
}
