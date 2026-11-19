package com.nexo.service.assistente;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexo.service.indexacao.TrechoEncontrado;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A linha final {@code FONTES:} decide quais trechos viram fonte da resposta. */
class FontesDaRespostaTest {

    private static final TrechoEncontrado A = trecho("Férias");
    private static final TrechoEncontrado B = trecho("Reembolso");
    private static final TrechoEncontrado C = trecho("Atendimento");
    private static final List<TrechoEncontrado> ENVIADOS = List.of(A, B, C);

    @Test
    void soOsTrechosDeclaradosViramFonteEALinhaSaiDoTexto() {
        FontesDaResposta separada = FontesDaResposta.separar(
                "São 30 dias (Política de Férias).\n\nFONTES: 3, 1", ENVIADOS);

        assertThat(separada.texto()).isEqualTo("São 30 dias (Política de Férias).");
        assertThat(separada.usados()).containsExactly(A, C);
    }

    @Test
    void nenhumaFonteDeixaARespostaSemFontes() {
        FontesDaResposta separada = FontesDaResposta.separar("Olá! Como posso ajudar?\nFONTES: nenhuma", ENVIADOS);

        assertThat(separada.texto()).isEqualTo("Olá! Como posso ajudar?");
        assertThat(separada.usados()).isEmpty();
    }

    @Test
    void respostaSemALinhaMantemTodosOsTrechos() {
        FontesDaResposta separada = FontesDaResposta.separar("  Trinta dias.  ", ENVIADOS);

        assertThat(separada.texto()).isEqualTo("Trinta dias.");
        assertThat(separada.usados()).containsExactly(A, B, C);
    }

    @Test
    void aceitaMarcacaoDeNegritoENumeroForaDaListaEhIgnorado() {
        FontesDaResposta separada = FontesDaResposta.separar("Resposta.\n**Fontes: 2, 9, 2**", ENVIADOS);

        assertThat(separada.texto()).isEqualTo("Resposta.");
        assertThat(separada.usados()).containsExactly(B);
    }

    private static TrechoEncontrado trecho(String titulo) {
        return new TrechoEncontrado(UUID.randomUUID(), UUID.randomUUID(), titulo, 1, "texto", 0.2);
    }
}
