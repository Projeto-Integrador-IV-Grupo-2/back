package com.nexo.ia;

import com.nexo.config.IaProperties;
import com.nexo.event.ChamadaIaRealizadaEvent;
import com.nexo.event.ChamadaIaRealizadaEvent.Operacao;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Envolve o {@link ClienteIa} escolhido pela {@link ClienteIaFactory} e, a
 * cada chamada, publica {@link ChamadaIaRealizadaEvent} com modelo, tokens e
 * duração.
 *
 * <p>Observer: o cliente não sabe quem registra o uso, e as implementações
 * reais ({@link GeminiClienteIa}, {@link ClienteIaFake}) continuam sem
 * conhecer eventos. O resultado e as exceções passam intactos: avisar o uso
 * nunca muda a resposta nem derruba a chamada.</p>
 */
public class ClienteIaComRegistroDeUso implements ClienteIa {

    private static final Logger log = LoggerFactory.getLogger(ClienteIaComRegistroDeUso.class);

    private final ClienteIa cliente;
    private final ApplicationEventPublisher publisher;
    private final IaProperties properties;

    public ClienteIaComRegistroDeUso(ClienteIa cliente, ApplicationEventPublisher publisher,
                                     IaProperties properties) {
        this.cliente = cliente;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Override
    public RespostaIa gerarResposta(String promptSistema, List<MensagemIa> mensagens) {
        long inicio = System.nanoTime();
        try {
            RespostaIa resposta = cliente.gerarResposta(promptSistema, mensagens);
            avisar(properties.modelo(), Operacao.GERACAO, resposta.tokensEntrada(), resposta.tokensSaida(), 1,
                    inicio, true);
            return resposta;
        } catch (RuntimeException ex) {
            avisar(properties.modelo(), Operacao.GERACAO, 0, 0, 1, inicio, false);
            throw ex;
        }
    }

    @Override
    public List<float[]> gerarEmbeddings(List<String> textos, FinalidadeEmbedding finalidade) {
        long inicio = System.nanoTime();
        int itens = textos == null ? 0 : textos.size();
        try {
            List<float[]> vetores = cliente.gerarEmbeddings(textos, finalidade);
            avisar(properties.modeloEmbedding(), Operacao.EMBEDDING, 0, 0, itens, inicio, true);
            return vetores;
        } catch (RuntimeException ex) {
            avisar(properties.modeloEmbedding(), Operacao.EMBEDDING, 0, 0, itens, inicio, false);
            throw ex;
        }
    }

    private void avisar(String modelo, Operacao operacao, int tokensEntrada, int tokensSaida, int itens,
                        long inicio, boolean sucesso) {
        try {
            publisher.publishEvent(new ChamadaIaRealizadaEvent(properties.provedor(), modelo, operacao,
                    tokensEntrada, tokensSaida, itens, Duration.ofNanos(System.nanoTime() - inicio), sucesso));
        } catch (RuntimeException ex) {
            // Registrar uso é secundário: nunca troca a resposta (ou o erro) do modelo.
            log.warn("Falha ao registrar o uso da IA ({} {}).", operacao, modelo, ex);
        }
    }
}
