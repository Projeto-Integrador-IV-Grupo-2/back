package com.nexo.service.recomendacao;

import java.util.List;

/**
 * O que o modelo pode saber do colaborador na hora de indicar trilhas.
 *
 * <p>Só área, cargo, o que ele já estudou e as competências do PDI —
 * <strong>nunca</strong> nome, e-mail ou qualquer identificador. Como é um
 * record de textos simples, montado dentro da transação do
 * {@link ColetorDeDadosDaSugestao}, pode atravessar a chamada ao modelo sem
 * depender da sessão do Hibernate.</p>
 */
public record PerfilDoUsuario(String area, String cargo, List<String> trilhasConcluidas,
                              List<String> trilhasEmAndamento, List<String> competenciasDoPdi) {

    public PerfilDoUsuario {
        trilhasConcluidas = trilhasConcluidas == null ? List.of() : List.copyOf(trilhasConcluidas);
        trilhasEmAndamento = trilhasEmAndamento == null ? List.of() : List.copyOf(trilhasEmAndamento);
        competenciasDoPdi = competenciasDoPdi == null ? List.of() : List.copyOf(competenciasDoPdi);
    }
}
