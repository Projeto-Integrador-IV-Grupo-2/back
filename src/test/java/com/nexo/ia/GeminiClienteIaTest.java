package com.nexo.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nexo.config.IaProperties;
import com.nexo.exception.IaIndisponivelException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Chamadas ao Gemini sem rede (MockRestServiceServer): sucesso, novas tentativas e falhas. */
class GeminiClienteIaTest {

    private static final String BASE = "https://gemini.test/v1beta";
    private static final String GERAR = BASE + "/models/gemini-teste:generateContent";
    private static final String EMBEDDINGS = BASE + "/models/embedding-teste:batchEmbedContents";

    private static final String RESPOSTA_OK = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"Olá! "},{"text":"Tudo certo."}]}}],
             "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":5}}
            """;

    private MockRestServiceServer servidor;

    private GeminiClienteIa cliente(String chave, int maxTentativas) {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        IaProperties properties = new IaProperties("gemini", chave, BASE, "gemini-teste", "embedding-teste", 3,
                Duration.ofSeconds(1), maxTentativas, null, null, null, null);
        return new GeminiClienteIa(builder.build(), properties, Duration.ZERO);
    }

    @Test
    void geraRespostaComPromptDeSistemaHistoricoEChaveNoHeader() {
        GeminiClienteIa cliente = cliente("chave-secreta", 3);
        servidor.expect(requestTo(GERAR))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "chave-secreta"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("Seja breve."))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[1].role").value("model"))
                .andExpect(jsonPath("$.contents[2].parts[0].text").value("E agora?"))
                .andRespond(withSuccess(RESPOSTA_OK, MediaType.APPLICATION_JSON));

        RespostaIa resposta = cliente.gerarResposta("Seja breve.", List.of(
                MensagemIa.doUsuario("Oi"), MensagemIa.doAssistente("Olá"), MensagemIa.doUsuario("E agora?")));

        assertThat(resposta.texto()).isEqualTo("Olá! Tudo certo.");
        assertThat(resposta.tokensEntrada()).isEqualTo(12);
        assertThat(resposta.tokensSaida()).isEqualTo(5);
        servidor.verify();
    }

    @Test
    void limiteDeUsoGanhaNovaTentativa() {
        GeminiClienteIa cliente = cliente("chave", 3);
        servidor.expect(requestTo(GERAR)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        servidor.expect(requestTo(GERAR)).andRespond(withSuccess(RESPOSTA_OK, MediaType.APPLICATION_JSON));

        RespostaIa resposta = cliente.gerarResposta("s", List.of(MensagemIa.doUsuario("p")));

        assertThat(resposta.texto()).isEqualTo("Olá! Tudo certo.");
        servidor.verify();
    }

    @Test
    void limiteDeUsoPersistenteViraIaIndisponivel() {
        GeminiClienteIa cliente = cliente("chave", 2);
        servidor.expect(times(2), requestTo(GERAR)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> cliente.gerarResposta("s", List.of(MensagemIa.doUsuario("p"))))
                .isInstanceOf(IaIndisponivelException.class)
                .hasMessageContaining("limite de uso");
        servidor.verify();
    }

    @Test
    void timeoutEsgotaAsTentativasEViraIaIndisponivel() {
        GeminiClienteIa cliente = cliente("chave", 3);
        servidor.expect(times(3), requestTo(GERAR)).andRespond(withException(new SocketTimeoutException("lento")));

        assertThatThrownBy(() -> cliente.gerarResposta("s", List.of(MensagemIa.doUsuario("p"))))
                .isInstanceOf(IaIndisponivelException.class)
                .hasMessageContaining("indisponível");
        servidor.verify();
    }

    @Test
    void erroDoProvedorSeguidoDeSucessoResponde() {
        GeminiClienteIa cliente = cliente("chave", 3);
        servidor.expect(requestTo(GERAR)).andRespond(withServerError());
        servidor.expect(requestTo(GERAR)).andRespond(withSuccess(RESPOSTA_OK, MediaType.APPLICATION_JSON));

        assertThat(cliente.gerarResposta("s", List.of(MensagemIa.doUsuario("p"))).texto()).isNotBlank();
        servidor.verify();
    }

    @Test
    void chaveRecusadaNaoGanhaNovaTentativa() {
        GeminiClienteIa cliente = cliente("chave-errada", 3);
        servidor.expect(times(1), requestTo(GERAR)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> cliente.gerarResposta("s", List.of(MensagemIa.doUsuario("p"))))
                .isInstanceOf(IaIndisponivelException.class)
                .hasMessageNotContaining("chave-errada");
        servidor.verify();
    }

    @Test
    void semChaveRespondeIndisponivelSemChamarARede() {
        GeminiClienteIa cliente = cliente("", 3);

        assertThatThrownBy(() -> cliente.gerarResposta("s", List.of(MensagemIa.doUsuario("p"))))
                .isInstanceOf(IaIndisponivelException.class)
                .hasMessageContaining("não está configurado");
        assertThatThrownBy(() -> cliente.gerarEmbedding("texto", FinalidadeEmbedding.CONSULTA))
                .isInstanceOf(IaIndisponivelException.class);
        servidor.verify();
    }

    @Test
    void geraEmbeddingsEmLoteNaOrdemComATarefaCerta() {
        GeminiClienteIa cliente = cliente("chave", 3);
        servidor.expect(requestTo(EMBEDDINGS))
                .andExpect(jsonPath("$.requests.length()").value(2))
                .andExpect(jsonPath("$.requests[0].taskType").value("RETRIEVAL_DOCUMENT"))
                .andExpect(jsonPath("$.requests[0].outputDimensionality").value(3))
                .andExpect(jsonPath("$.requests[1].content.parts[0].text").value("segundo"))
                .andRespond(withSuccess("""
                        {"embeddings":[{"values":[0.1,0.2,0.3]},{"values":[0.4,0.5,0.6]}]}
                        """, MediaType.APPLICATION_JSON));

        List<float[]> vetores = cliente.gerarEmbeddings(List.of("primeiro", "segundo"), FinalidadeEmbedding.DOCUMENTO);

        assertThat(vetores).hasSize(2);
        assertThat(vetores.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(vetores.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
        servidor.verify();
    }

    @Test
    void embeddingDeTamanhoErradoViraIaIndisponivel() {
        GeminiClienteIa cliente = cliente("chave", 3);
        servidor.expect(requestTo(EMBEDDINGS))
                .andExpect(jsonPath("$.requests[0].taskType").value("RETRIEVAL_QUERY"))
                .andRespond(withSuccess("{\"embeddings\":[{\"values\":[0.1]}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.gerarEmbedding("pergunta", FinalidadeEmbedding.CONSULTA))
                .isInstanceOf(IaIndisponivelException.class);
    }
}
