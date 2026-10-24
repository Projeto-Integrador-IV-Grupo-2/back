package com.nexo.service.indexacao;

import com.nexo.config.IaProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Divide um texto em trechos de tamanho limitado, com sobreposição, na ordem
 * em que aparecem.
 *
 * <p>Cada trecho termina, de preferência, num fim de parágrafo; senão, num fim
 * de frase; senão, num espaço. Só corta no meio de uma palavra quando não há
 * espaço nenhum na janela. A sobreposição repete o fim de um trecho no começo
 * do seguinte, para uma frase cortada na borda continuar encontrável.</p>
 *
 * <p>Singleton sem estado: os limites são fixados na construção.</p>
 */
@Component
public class DivisorDeTexto {

    private final int tamanho;
    private final int sobreposicao;

    @Autowired
    public DivisorDeTexto(IaProperties properties) {
        this(properties.indexacao().tamanhoTrecho(), properties.indexacao().sobreposicao());
    }

    DivisorDeTexto(int tamanho, int sobreposicao) {
        if (tamanho <= 0 || sobreposicao < 0 || sobreposicao >= tamanho) {
            throw new IllegalArgumentException("Tamanho do trecho e sobreposição inválidos");
        }
        this.tamanho = tamanho;
        this.sobreposicao = sobreposicao;
    }

    public List<String> dividir(String texto) {
        String limpo = normalizar(texto);
        List<String> trechos = new ArrayList<>();
        if (limpo.isEmpty()) {
            return trechos;
        }
        int inicio = 0;
        while (inicio < limpo.length()) {
            int fim = Math.min(inicio + tamanho, limpo.length());
            if (fim < limpo.length()) {
                fim = melhorCorte(limpo, inicio, fim);
            }
            String trecho = limpo.substring(inicio, fim).strip();
            if (!trecho.isEmpty()) {
                trechos.add(trecho);
            }
            if (fim >= limpo.length()) {
                break;
            }
            inicio = proximoInicio(limpo, inicio, fim);
        }
        return trechos;
    }

    /** Último corte bom entre a metade da janela e o fim dela. */
    private static int melhorCorte(String texto, int inicio, int fim) {
        int minimo = inicio + (fim - inicio) / 2;
        int paragrafo = texto.lastIndexOf("\n\n", fim - 1);
        if (paragrafo >= minimo) {
            return paragrafo + 2;
        }
        for (int i = fim - 1; i >= minimo; i--) {
            char c = texto.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == ';' || c == ':')
                    && i + 1 < texto.length() && Character.isWhitespace(texto.charAt(i + 1))) {
                return i + 1;
            }
        }
        for (int i = fim - 1; i >= minimo; i--) {
            if (Character.isWhitespace(texto.charAt(i))) {
                return i + 1;
            }
        }
        return fim;
    }

    /** Volta a sobreposição e avança até o começo de uma palavra, sempre andando para frente. */
    private int proximoInicio(String texto, int inicio, int fim) {
        int proximo = Math.max(fim - sobreposicao, inicio + 1);
        while (proximo < fim && proximo > 0 && !Character.isWhitespace(texto.charAt(proximo - 1))) {
            proximo++;
        }
        return proximo;
    }

    /** Une espaços repetidos e no máximo uma linha em branco entre parágrafos. */
    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f\\u00A0 ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
