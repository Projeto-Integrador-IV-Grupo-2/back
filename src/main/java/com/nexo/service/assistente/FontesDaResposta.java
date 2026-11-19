package com.nexo.service.assistente;

import com.nexo.service.indexacao.TrechoEncontrado;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Separa, na resposta do modelo, o texto para o usuário da linha final
 * {@code FONTES: 1, 3} — os trechos que ele disse ter usado.
 *
 * <p>A busca semântica sempre devolve os trechos <em>mais parecidos</em>, não
 * necessariamente relevantes: sem esta linha, um "olá" ou uma pergunta sem
 * resposta nos documentos apareceria com fontes que não têm nada a ver.</p>
 *
 * <p>Resposta sem a linha (o modelo esqueceu, ou é o cliente fake dos testes)
 * mantém todos os trechos enviados, como antes. Números fora da lista são
 * ignorados; se nenhum número válido sobrar, a resposta fica sem fontes.</p>
 *
 * @param texto  resposta sem a linha de fontes
 * @param usados trechos citados, na ordem em que foram enviados ao modelo
 */
record FontesDaResposta(String texto, List<TrechoEncontrado> usados) {

    private static final Pattern LINHA = Pattern.compile(
            "(?im)^[\\s>*_`-]*FONTES?\\s*:\\s*(.*?)[\\s*_`.]*$");
    private static final Pattern NUMERO = Pattern.compile("\\d+");

    static FontesDaResposta separar(String resposta, List<TrechoEncontrado> enviados) {
        String texto = resposta == null ? "" : resposta;
        Matcher linha = LINHA.matcher(texto);
        String declaradas = null;
        int inicio = -1;
        while (linha.find()) {
            // Vale a última ocorrência: é onde o prompt manda escrever.
            declaradas = linha.group(1);
            inicio = linha.start();
        }
        if (declaradas == null) {
            return new FontesDaResposta(texto.strip(), List.copyOf(enviados));
        }
        Set<Integer> numeros = new TreeSet<>();
        Matcher numero = NUMERO.matcher(declaradas);
        while (numero.find()) {
            try {
                numeros.add(Integer.parseInt(numero.group()));
            } catch (NumberFormatException ex) {
                // Número absurdo de grande: não é um trecho.
            }
        }
        List<TrechoEncontrado> usados = new ArrayList<>();
        for (int n : numeros) {
            if (n >= 1 && n <= enviados.size()) {
                usados.add(enviados.get(n - 1));
            }
        }
        return new FontesDaResposta(texto.substring(0, inicio).strip(), List.copyOf(usados));
    }
}
