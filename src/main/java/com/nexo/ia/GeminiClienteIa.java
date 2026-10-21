package com.nexo.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexo.config.IaProperties;
import com.nexo.exception.IaIndisponivelException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link ClienteIa} do Google Gemini, pela API REST {@code v1beta}.
 *
 * <p>Sem estado: o {@link RestClient} vem pronto de {@code IaConfig} e é
 * reaproveitado por todas as chamadas. Quem cria a instância é a
 * {@link ClienteIaFactory} — o resto do sistema só enxerga {@link ClienteIa}.</p>
 *
 * <p>Timeout, 429 e 5xx ganham novas tentativas, com espera crescente, até
 * {@code app.ia.max-tentativas}. Outros 4xx (chave inválida, pedido recusado)
 * falham na hora. Toda falha vira {@link IaIndisponivelException} (503).</p>
 *
 * <p>A chave vai só no header {@code x-goog-api-key}. Os logs registram o
 * status HTTP, nunca o prompt, a resposta ou a chave.</p>
 */
public class GeminiClienteIa implements ClienteIa {

    private static final Logger log = LoggerFactory.getLogger(GeminiClienteIa.class);

    /** Limite de textos por chamada de {@code batchEmbedContents}. */
    static final int LOTE_EMBEDDINGS = 100;

    private static final String HEADER_CHAVE = "x-goog-api-key";
    private static final double TEMPERATURA = 0.2;

    private final RestClient restClient;
    private final IaProperties properties;
    private final Duration esperaBase;

    public GeminiClienteIa(RestClient restClient, IaProperties properties) {
        this(restClient, properties, Duration.ofMillis(500));
    }

    /** Construtor dos testes: permite zerar a espera entre tentativas. */
    GeminiClienteIa(RestClient restClient, IaProperties properties, Duration esperaBase) {
        this.restClient = restClient;
        this.properties = properties;
        this.esperaBase = esperaBase;
    }

    @Override
    public RespostaIa gerarResposta(String promptSistema, List<MensagemIa> mensagens) {
        exigirChave();
        Map<String, Object> corpo = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", promptSistema))),
                "contents", mensagens.stream().map(GeminiClienteIa::conteudo).toList(),
                "generationConfig", Map.of("temperature", TEMPERATURA));

        RespostaGeracao resposta = comNovasTentativas(() -> restClient.post()
                .uri(properties.urlBase() + "/models/{modelo}:generateContent", properties.modelo())
                .header(HEADER_CHAVE, properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(RespostaGeracao.class));

        String texto = resposta == null ? null : resposta.texto();
        if (texto == null || texto.isBlank()) {
            throw new IaIndisponivelException("O assistente de IA não gerou resposta. Tente novamente.");
        }
        RespostaGeracao.Uso uso = resposta.usageMetadata();
        return new RespostaIa(texto.strip(),
                uso == null || uso.promptTokenCount() == null ? 0 : uso.promptTokenCount(),
                uso == null || uso.candidatesTokenCount() == null ? 0 : uso.candidatesTokenCount());
    }

    @Override
    public List<float[]> gerarEmbeddings(List<String> textos, FinalidadeEmbedding finalidade) {
        exigirChave();
        List<float[]> vetores = new ArrayList<>(textos.size());
        for (int inicio = 0; inicio < textos.size(); inicio += LOTE_EMBEDDINGS) {
            List<String> lote = textos.subList(inicio, Math.min(inicio + LOTE_EMBEDDINGS, textos.size()));
            vetores.addAll(gerarLote(lote, finalidade));
        }
        return vetores;
    }

    private List<float[]> gerarLote(List<String> lote, FinalidadeEmbedding finalidade) {
        String modelo = "models/" + properties.modeloEmbedding();
        String tipoTarefa = finalidade == FinalidadeEmbedding.CONSULTA ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT";
        List<Map<String, Object>> pedidos = lote.stream()
                .map(texto -> Map.<String, Object>of(
                        "model", modelo,
                        "content", Map.of("parts", List.of(Map.of("text", texto))),
                        "taskType", tipoTarefa,
                        "outputDimensionality", properties.dimensoes()))
                .toList();

        RespostaEmbeddings resposta = comNovasTentativas(() -> restClient.post()
                .uri(properties.urlBase() + "/models/{modelo}:batchEmbedContents", properties.modeloEmbedding())
                .header(HEADER_CHAVE, properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requests", pedidos))
                .retrieve()
                .body(RespostaEmbeddings.class));

        if (resposta == null || resposta.embeddings() == null || resposta.embeddings().size() != lote.size()) {
            throw new IaIndisponivelException("O provedor de IA devolveu embeddings incompletos.");
        }
        return resposta.embeddings().stream().map(e -> paraVetor(e.values())).toList();
    }

    private float[] paraVetor(List<Double> valores) {
        if (valores == null || valores.size() != properties.dimensoes()) {
            throw new IaIndisponivelException("O provedor de IA devolveu um embedding de tamanho inesperado.");
        }
        float[] vetor = new float[valores.size()];
        for (int i = 0; i < vetor.length; i++) {
            vetor[i] = valores.get(i).floatValue();
        }
        return vetor;
    }

    private <T> T comNovasTentativas(Supplier<T> chamada) {
        int maxTentativas = properties.maxTentativas();
        for (int tentativa = 1; ; tentativa++) {
            try {
                return chamada.get();
            } catch (HttpClientErrorException ex) {
                if (ex.getStatusCode().value() != 429) {
                    log.warn("Provedor de IA recusou a chamada com HTTP {}", ex.getStatusCode().value());
                    throw new IaIndisponivelException(
                            "O provedor de IA recusou a chamada. Verifique a configuração do assistente.", ex);
                }
                if (tentativa >= maxTentativas) {
                    throw new IaIndisponivelException(
                            "O assistente de IA atingiu o limite de uso. Tente novamente em instantes.", ex);
                }
                log.info("Provedor de IA respondeu 429; tentativa {} de {}", tentativa, maxTentativas);
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                if (tentativa >= maxTentativas) {
                    log.warn("Provedor de IA indisponível após {} tentativas: {}", tentativa, ex.getClass().getSimpleName());
                    throw new IaIndisponivelException(
                            "O assistente de IA está indisponível no momento. Tente novamente em instantes.", ex);
                }
                log.info("Falha temporária do provedor de IA ({}); tentativa {} de {}",
                        ex.getClass().getSimpleName(), tentativa, maxTentativas);
            } catch (RestClientException ex) {
                log.warn("Resposta inesperada do provedor de IA: {}", ex.getClass().getSimpleName());
                throw new IaIndisponivelException(
                        "O assistente de IA está indisponível no momento. Tente novamente em instantes.", ex);
            }
            esperar(tentativa);
        }
    }

    private void esperar(int tentativa) {
        long millis = esperaBase.toMillis() * (1L << (tentativa - 1));
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IaIndisponivelException("A chamada ao assistente de IA foi interrompida.", ex);
        }
    }

    private void exigirChave() {
        if (!properties.temChave()) {
            throw new IaIndisponivelException(
                    "O assistente de IA não está configurado neste ambiente (IA_API_KEY ausente).");
        }
    }

    private static Map<String, Object> conteudo(MensagemIa mensagem) {
        String papel = mensagem.papel() == PapelIa.ASSISTENTE ? "model" : "user";
        return Map.of("role", papel, "parts", List.of(Map.of("text", mensagem.texto())));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaGeracao(List<Candidato> candidates, Uso usageMetadata) {

        String texto() {
            if (candidates == null || candidates.isEmpty() || candidates.get(0).content() == null
                    || candidates.get(0).content().parts() == null) {
                return null;
            }
            StringBuilder texto = new StringBuilder();
            candidates.get(0).content().parts().forEach(parte -> {
                if (parte.text() != null) {
                    texto.append(parte.text());
                }
            });
            return texto.toString();
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Candidato(Conteudo content) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Conteudo(List<Parte> parts) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Parte(String text) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Uso(Integer promptTokenCount, Integer candidatesTokenCount) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaEmbeddings(List<Embedding> embeddings) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Embedding(List<Double> values) {
        }
    }
}
