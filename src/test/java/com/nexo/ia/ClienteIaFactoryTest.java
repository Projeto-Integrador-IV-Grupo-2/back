package com.nexo.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexo.config.IaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Factory Method do cliente de IA: o provedor configurado escolhe a implementação. */
class ClienteIaFactoryTest {

    private final RestClient restClient = RestClient.create();

    @Test
    void provedorFakeEntregaOClienteSimulado() {
        assertThat(ClienteIaFactory.criar(propriedades("fake"), restClient)).isInstanceOf(ClienteIaFake.class);
    }

    @Test
    void provedorGeminiEntregaOClienteReal() {
        assertThat(ClienteIaFactory.criar(propriedades("gemini"), restClient)).isInstanceOf(GeminiClienteIa.class);
    }

    @Test
    void ignoraMaiusculasNoNomeDoProvedor() {
        assertThat(ClienteIaFactory.criar(propriedades("FAKE"), restClient)).isInstanceOf(ClienteIaFake.class);
    }

    @Test
    void semProvedorConfiguradoUsaOGemini() {
        assertThat(ClienteIaFactory.criar(propriedades(null), restClient)).isInstanceOf(GeminiClienteIa.class);
    }

    @Test
    void provedorDesconhecidoImpedeASubida() {
        assertThatThrownBy(() -> ClienteIaFactory.criar(propriedades("openai"), restClient))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openai");
    }

    private static IaProperties propriedades(String provedor) {
        return new IaProperties(provedor, "chave-de-teste", null, null, null, null, null, null, null,
                null, null, null);
    }
}
