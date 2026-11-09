package com.nexo.service.assistente;

import com.nexo.config.IaProperties;
import com.nexo.dto.assistente.FonteAssistenteResponse;
import com.nexo.dto.assistente.MensagemResponse;
import com.nexo.dto.assistente.RespostaAssistenteResponse;
import com.nexo.dto.assistente.SessaoResponse;
import com.nexo.entity.ChatMensagem;
import com.nexo.entity.ChatSessao;
import com.nexo.entity.FonteMensagem;
import com.nexo.event.PerguntaRespondidaEvent;
import com.nexo.ia.ClienteIa;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.indexacao.BuscaDeTrechosService;
import com.nexo.service.indexacao.TrechoEncontrado;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Perguntas ao assistente sobre os documentos da empresa (issue #60).
 *
 * <p>Fluxo: confere a sessão (404 se não for do usuário) → limite por minuto
 * (429) → busca os trechos do tenant → monta o prompt → chama o modelo →
 * grava pergunta e resposta juntas → publica {@link PerguntaRespondidaEvent}.</p>
 *
 * <p><strong>Sem {@code @Transactional} de propósito:</strong> a chamada ao
 * modelo pode levar segundos e não deve segurar conexão do banco. A gravação
 * acontece só no fim, numa transação própria — se o provedor falhar (503),
 * nada fica gravado pela metade.</p>
 *
 * <p>Sem nenhum trecho relevante, responde "não encontrei" sem chamar o
 * modelo: assim ele não tem como inventar.</p>
 *
 * <p>Singleton sem estado. Nenhum log carrega a pergunta ou a resposta.</p>
 */
@Service
public class ConsultaAssistenteService {

    public static final String RESPOSTA_NAO_ENCONTREI =
            "Não encontrei essa informação nos documentos da empresa. "
                    + "Tente reformular a pergunta ou procure a área responsável pelo assunto.";

    private final ChatSessaoService chatSessaoService;
    private final BuscaDeTrechosService buscaDeTrechosService;
    private final MontadorDePromptFactory montadorDePromptFactory;
    private final ClienteIa clienteIa;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final LimitePerguntasService limitePerguntasService;
    private final int trechos;
    private final int perguntasPorMinuto;
    private final int mensagensHistorico;

    public ConsultaAssistenteService(ChatSessaoService chatSessaoService,
                                     BuscaDeTrechosService buscaDeTrechosService,
                                     MontadorDePromptFactory montadorDePromptFactory,
                                     ClienteIa clienteIa,
                                     ApplicationEventPublisher publisher,
                                     Clock clock,
                                     IaProperties properties,
                                     LimitePerguntasService limitePerguntasService) {
        this.chatSessaoService = chatSessaoService;
        this.buscaDeTrechosService = buscaDeTrechosService;
        this.montadorDePromptFactory = montadorDePromptFactory;
        this.clienteIa = clienteIa;
        this.publisher = publisher;
        this.clock = clock;
        this.limitePerguntasService = limitePerguntasService;
        this.trechos = properties.busca().trechos();
        this.perguntasPorMinuto = properties.consulta().perguntasPorMinuto();
        this.mensagensHistorico = properties.consulta().mensagensHistorico();
    }

    public RespostaAssistenteResponse perguntar(UUID sessaoId, String pergunta, UsuarioAutenticado usuario) {
        ChatSessao sessao = chatSessaoService.buscarDoUsuario(sessaoId, usuario);
        MontadorDePrompt montador = montadorDePromptFactory.para(sessao.getContexto());
        OffsetDateTime agora = OffsetDateTime.now(clock);
        limitePerguntasService.reservar(usuario.getTenantId(), usuario.getId(), perguntasPorMinuto);

        String texto = pergunta.strip();
        List<TrechoEncontrado> encontrados = buscaDeTrechosService.buscar(usuario.getTenantId(), texto, trechos);

        String resposta;
        if (encontrados.isEmpty()) {
            resposta = RESPOSTA_NAO_ENCONTREI;
        } else {
            List<ChatMensagem> historico = chatSessaoService.ultimasMensagens(sessaoId, mensagensHistorico);
            PromptMontado prompt = montador.montar(texto, encontrados, historico);
            resposta = clienteIa.gerarResposta(prompt.sistema(), prompt.mensagens()).texto();
        }

        List<FonteMensagem> fontes = encontrados.stream()
                .map(trecho -> new FonteMensagem(trecho.conteudoId(), trecho.titulo(), trecho.chunkId(),
                        trecho.ordem(), trecho.texto()))
                .toList();
        TrocaRegistrada troca = chatSessaoService.registrarTroca(sessao, texto, agora, resposta, fontes);
        MensagemResponse daResposta = MensagemResponse.de(troca.resposta());
        List<FonteAssistenteResponse> fontesDetalhadas = troca.resposta().getFontes().stream()
                .map(FonteAssistenteResponse::de)
                .toList();
        long documentosCitados = fontes.stream()
                .map(FonteMensagem::conteudoId)
                .distinct()
                .count();

        publisher.publishEvent(new PerguntaRespondidaEvent(usuario.getTenantId(), usuario.getId(), sessaoId,
                troca.resposta().getId(), documentosCitados > 0, Math.toIntExact(documentosCitados)));
        return new RespostaAssistenteResponse(SessaoResponse.de(troca.sessao()),
                MensagemResponse.de(troca.pergunta()), daResposta, fontesDetalhadas);
    }
}
