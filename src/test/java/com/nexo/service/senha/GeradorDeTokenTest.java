package com.nexo.service.senha;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexo.service.senha.GeradorDeToken.TokenGerado;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeradorDeTokenTest {

    private final GeradorDeToken gerador = new GeradorDeToken();

    @Test
    void geraTokenAleatorioQueCabeNaUrlEOHashQueVaiParaOBanco() {
        TokenGerado token = gerador.gerar();

        // 32 bytes em Base64 URL sem "=": 43 caracteres, sem + nem /.
        assertThat(token.valor()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(token.hash()).hasSize(64).matches("[0-9a-f]+").isEqualTo(gerador.hash(token.valor()));
        assertThat(token.hash()).isNotEqualTo(token.valor());
    }

    @Test
    void tokensNaoSeRepetem() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(vistos.add(gerador.gerar().valor())).isTrue();
        }
    }

    @Test
    void hashEhDeterministico() {
        assertThat(gerador.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void toStringNuncaMostraOToken() {
        TokenGerado token = gerador.gerar();

        assertThat(token.toString()).doesNotContain(token.valor()).doesNotContain(token.hash());
    }
}
