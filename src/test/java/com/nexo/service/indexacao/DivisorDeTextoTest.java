package com.nexo.service.indexacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DivisorDeTextoTest {

    private final DivisorDeTexto divisor = new DivisorDeTexto(100, 20);

    @Test
    void textoVazioOuNuloNaoGeraTrecho() {
        assertThat(divisor.dividir(null)).isEmpty();
        assertThat(divisor.dividir("   \n\n  ")).isEmpty();
    }

    @Test
    void textoCurtoViraUmTrechoSo() {
        assertThat(divisor.dividir("  Regras de   home office.  ")).containsExactly("Regras de home office.");
    }

    @Test
    void nenhumTrechoPassaDoLimite() {
        String texto = "Frase número um do documento. ".repeat(40);

        List<String> trechos = divisor.dividir(texto);

        assertThat(trechos).hasSizeGreaterThan(1);
        assertThat(trechos).allSatisfy(trecho -> assertThat(trecho.length()).isLessThanOrEqualTo(100));
    }

    @Test
    void trechosVizinhosSeSobrepoem() {
        String texto = palavrasNumeradas(80);

        List<String> trechos = divisor.dividir(texto);

        for (int i = 1; i < trechos.size(); i++) {
            String anterior = trechos.get(i - 1);
            String primeiraPalavra = trechos.get(i).split(" ")[0];
            assertThat(anterior).contains(primeiraPalavra);
        }
    }

    @Test
    void mantemAOrdemECobreOTextoTodo() {
        String texto = palavrasNumeradas(80);

        List<String> trechos = divisor.dividir(texto);

        assertThat(trechos.get(0)).startsWith("palavra1 ");
        assertThat(trechos.get(trechos.size() - 1)).endsWith("palavra80");
        int ultimaVista = 0;
        for (String trecho : trechos) {
            int primeira = Integer.parseInt(trecho.split(" ")[0].substring("palavra".length()));
            assertThat(primeira).isGreaterThan(ultimaVista);
            ultimaVista = primeira;
        }
    }

    @Test
    void preferirCortarNoFimDoParagrafo() {
        String primeiro = "Primeiro parágrafo com algumas palavras para ocupar espaço.";
        String segundo = "Segundo parágrafo que continua o assunto e passa do limite do trecho.";

        List<String> trechos = divisor.dividir(primeiro + "\n\n" + segundo);

        assertThat(trechos.get(0)).isEqualTo(primeiro);
    }

    @Test
    void palavraGiganteSemEspacoEhCortadaSemTravar() {
        List<String> trechos = divisor.dividir("x".repeat(350));

        assertThat(trechos).isNotEmpty();
        assertThat(trechos).allSatisfy(trecho -> assertThat(trecho.length()).isLessThanOrEqualTo(100));
        assertThat(String.join("", trechos)).hasSizeGreaterThanOrEqualTo(350);
    }

    @Test
    void sobreposicaoMaiorQueOTrechoEhRecusada() {
        assertThatThrownBy(() -> new DivisorDeTexto(100, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String palavrasNumeradas(int quantidade) {
        StringBuilder texto = new StringBuilder();
        for (int i = 1; i <= quantidade; i++) {
            texto.append(i == 1 ? "" : " ").append("palavra").append(i);
        }
        return texto.toString();
    }
}
