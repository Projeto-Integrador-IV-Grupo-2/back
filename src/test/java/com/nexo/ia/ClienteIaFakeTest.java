package com.nexo.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClienteIaFakeTest {

    private final ClienteIaFake fake = new ClienteIaFake(768);

    @Test
    void textosQueDividemPalavrasFicamMaisProximos() {
        float[] pergunta = fake.gerarEmbedding("Qual é a política de férias?", FinalidadeEmbedding.CONSULTA);
        float[] parecido = fake.gerarEmbedding("A política de férias permite 30 dias", FinalidadeEmbedding.DOCUMENTO);
        float[] diferente = fake.gerarEmbedding("Manual do estacionamento da empresa", FinalidadeEmbedding.DOCUMENTO);

        assertThat(cosseno(pergunta, parecido)).isGreaterThan(cosseno(pergunta, diferente));
    }

    @Test
    void embeddingEhDeterministicoENormalizado() {
        float[] a = fake.gerarEmbedding("Benefícios do colaborador", FinalidadeEmbedding.DOCUMENTO);
        float[] b = fake.gerarEmbedding("beneficios do COLABORADOR", FinalidadeEmbedding.CONSULTA);

        assertThat(a).hasSize(768).containsExactly(b);
        assertThat(cosseno(a, a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void respostaContaOsDocumentosRecebidos() {
        RespostaIa resposta = fake.gerarResposta("sistema", List.of(MensagemIa.doUsuario(
                "<documento id=\"1\">a</documento><documento id=\"2\">b</documento> Pergunta")));

        assertThat(resposta.texto()).startsWith(ClienteIaFake.PREFIXO_RESPOSTA).contains("2 trecho(s)");
    }

    @Test
    void dianteDoCatalogoDeTrilhasRespondeOJsonDeSugestoes() {
        String turno = """
                <catalogo>
                <trilha id="11111111-1111-1111-1111-111111111111">
                Nome: Atendimento
                </trilha>
                <trilha id="22222222-2222-2222-2222-222222222222">
                Nome: LGPD
                </trilha>
                <trilha id="33333333-3333-3333-3333-333333333333">
                Nome: Negociação
                </trilha>
                </catalogo>

                Indique no máximo 2 trilhas para este colaborador.""";

        String resposta = fake.gerarResposta("sistema", List.of(MensagemIa.doUsuario(turno))).texto();

        assertThat(resposta)
                .startsWith("{\"sugestoes\":[")
                .contains("11111111-1111-1111-1111-111111111111")
                .contains("22222222-2222-2222-2222-222222222222")
                .contains(ClienteIaFake.MOTIVO_SIMULADO)
                // Respeita o limite pedido no prompt.
                .doesNotContain("33333333-3333-3333-3333-333333333333");
    }

    private static double cosseno(float[] a, float[] b) {
        double produto = 0;
        double normaA = 0;
        double normaB = 0;
        for (int i = 0; i < a.length; i++) {
            produto += a[i] * b[i];
            normaA += a[i] * a[i];
            normaB += b[i] * b[i];
        }
        return produto / (Math.sqrt(normaA) * Math.sqrt(normaB));
    }
}
