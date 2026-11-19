package com.nexo.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexo.config.IaProperties;
import com.nexo.exception.IaIndisponivelException;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
 * <p>O único estado é a pausa dos modelos que falharam; o {@link RestClient} vem pronto de {@code IaConfig} e é
 * reaproveitado por todas as chamadas. Quem cria a instância é a
 * {@link ClienteIaFactory} — o resto do sistema só enxerga {@link ClienteIa}.</p>
 *
 * <p>Timeout, 429 e 5xx ganham novas tentativas, com espera crescente, até
 * {@code app.ia.max-tentativas}. Outros 4xx (chave inválida, pedido recusado)
 * falham na hora. Toda falha vira {@link IaIndisponivelException} (503).</p>
 *
 * <p><strong>Modelos reserva:</strong> a geração de texto percorre
 * {@code app.ia.modelo} e depois {@code app.ia.modelos-reserva}, na ordem. Um
 * modelo sobrecarregado (429/5xx) passa a vez para o próximo na mesma rodada;
 * um que estourou o timeout ou foi aposentado pelo provedor (404) sai da
 * conversa até o fim da chamada, para o usuário não esperar o mesmo timeout
 * de novo. Quem falhou fica cinco minutos no fim da fila, para as próximas
 * perguntas não pagarem a mesma espera. Os embeddings usam um modelo só: trocar de modelo mudaria o espaço
 * vetorial dos trechos já indexados.</p>
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

    /** Por quanto tempo um modelo que falhou (429, 5xx ou timeout) cede a vez aos outros. */
    static final Duration PAUSA_DO_MODELO = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final IaProperties properties;
    private final Duration esperaBase;

    /** Até quando ({@link System#nanoTime()}) cada modelo que falhou fica no fim da fila. */
    private final Map<String, Long> emPausaAte = new ConcurrentHashMap<>();

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
    public boolean disponivel() {
        return properties.temChave();
    }

    @Override
    public RespostaIa gerarResposta(String promptSistema, List<MensagemIa> mensagens) {
        exigirChave();
        Map<String, Object> corpo = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", promptSistema))),
                "contents", mensagens.stream().map(GeminiClienteIa::conteudo).toList(),
                "generationConfig", Map.of("temperature", TEMPERATURA));

        RespostaGeracao resposta = comNovasTentativas(properties.modelosDeGeracao(), modelo -> restClient.post()
                .uri(properties.urlBase() + "/models/{modelo}:generateContent", modelo)
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

        RespostaEmbeddings resposta = comNovasTentativas(List.of(properties.modeloEmbedding()), nome -> restClient.post()
                .uri(properties.urlBase() + "/models/{modelo}:batchEmbedContents", nome)
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

    /**
     * Chama o provedor percorrendo {@code modelos} em rodadas, até
     * {@code app.ia.max-tentativas} rodadas, com espera crescente entre elas.
     */
    private <T> T comNovasTentativas(List<String> modelos, Function<String, T> chamada) {
        int maxTentativas = properties.maxTentativas();
        // Modelos que não adianta tentar de novo nesta chamada (aposentado ou timeout).
        Set<String> fora = new HashSet<>();
        boolean algumAposentado = false;
        boolean limiteDeUso = false;
        RuntimeException ultima = null;
        for (int tentativa = 1; tentativa <= maxTentativas && fora.size() < modelos.size(); tentativa++) {
            if (tentativa > 1) {
                esperar(tentativa - 1);
            }
            for (String modelo : saudaveisPrimeiro(modelos)) {
                if (fora.contains(modelo)) {
                    continue;
                }
                try {
                    return chamada.apply(modelo);
                } catch (HttpClientErrorException ex) {
                    int status = ex.getStatusCode().value();
                    ultima = ex;
                    if (status == 404) {
                        // O provedor aposenta modelos: o próximo da lista assume.
                        log.warn("Modelo de IA '{}' não existe mais no provedor (HTTP 404)", modelo);
                        fora.add(modelo);
                        algumAposentado = true;
                    } else if (status == 429) {
                        limiteDeUso = true;
                        pausar(modelo);
                        log.info("Provedor de IA respondeu 429 para '{}'; tentativa {} de {}",
                                modelo, tentativa, maxTentativas);
                    } else {
                        log.warn("Provedor de IA recusou a chamada com HTTP {}", status);
                        throw new IaIndisponivelException(
                                "O provedor de IA recusou a chamada. Verifique a configuração do assistente.", ex);
                    }
                } catch (RestClientException ex) {
                    boolean falhaDeRede = ex instanceof ResourceAccessException || causadaPorRede(ex);
                    if (!falhaDeRede && !(ex instanceof HttpServerErrorException)) {
                        log.warn("Resposta inesperada do provedor de IA: {}", ex.getClass().getSimpleName());
                        throw new IaIndisponivelException(
                                "O assistente de IA está indisponível no momento. Tente novamente em instantes.", ex);
                    }
                    ultima = ex;
                    limiteDeUso = false;
                    pausar(modelo);
                    log.info("Falha temporária do provedor de IA em '{}' ({}); tentativa {} de {}",
                            modelo, falhaDeRede ? "rede/timeout" : ex.getClass().getSimpleName(),
                            tentativa, maxTentativas);
                    if (falhaDeRede && modelos.size() > 1) {
                        // Já custou um timeout inteiro: com reserva disponível, não espera outro.
                        fora.add(modelo);
                    }
                }
            }
        }
        if (algumAposentado && ultima instanceof HttpClientErrorException erro
                && erro.getStatusCode().value() == 404) {
            throw new IaIndisponivelException(
                    "O provedor de IA recusou a chamada. Verifique a configuração do assistente "
                            + "(o modelo configurado não existe mais).", ultima);
        }
        if (limiteDeUso) {
            throw new IaIndisponivelException(
                    "O assistente de IA atingiu o limite de uso. Tente novamente em instantes.", ultima);
        }
        log.warn("Provedor de IA indisponível após {} tentativa(s) em {} modelo(s)", maxTentativas, modelos.size());
        throw new IaIndisponivelException(
                "O assistente de IA está indisponível no momento. Tente novamente em instantes.", ultima);
    }

    /**
     * O timeout pode estourar com a resposta já em leitura; aí o Spring embrulha
     * o {@link IOException} numa {@link RestClientException} comum, não numa
     * {@link ResourceAccessException}. É falha de rede do mesmo jeito.
     */
    private static boolean causadaPorRede(Throwable erro) {
        for (Throwable causa = erro.getCause(); causa != null; causa = causa.getCause()) {
            if (causa instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    /** Deixa o modelo no fim da fila por {@link #PAUSA_DO_MODELO}. */
    private void pausar(String modelo) {
        emPausaAte.put(modelo, System.nanoTime() + PAUSA_DO_MODELO.toNanos());
    }

    /**
     * Mesma lista, com os modelos que falharam há pouco no fim: um modelo
     * sobrecarregado costuma segurar a conexão por vários segundos antes de
     * responder 503, então as próximas perguntas vão direto para quem está
     * respondendo. Ninguém sai da lista — em pausa, só é tentado por último.
     */
    private List<String> saudaveisPrimeiro(List<String> modelos) {
        if (modelos.size() < 2 || emPausaAte.isEmpty()) {
            return modelos;
        }
        long agora = System.nanoTime();
        List<String> ordenados = new ArrayList<>(modelos.size());
        List<String> pausados = new ArrayList<>();
        for (String modelo : modelos) {
            Long ate = emPausaAte.get(modelo);
            if (ate != null && ate - agora > 0) {
                pausados.add(modelo);
            } else {
                ordenados.add(modelo);
            }
        }
        ordenados.addAll(pausados);
        return ordenados;
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
