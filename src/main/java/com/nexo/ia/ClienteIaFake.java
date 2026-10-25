package com.nexo.ia;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link ClienteIa} determinístico, sem rede — usado no perfil {@code test}
 * ({@code app.ia.provedor=fake}).
 *
 * <p>O embedding é um "saco de palavras" com hash: cada palavra (sem acento,
 * em minúsculas, com 3+ letras) soma 1 numa das dimensões, e o vetor é
 * normalizado. Textos que dividem palavras ficam próximos na distância de
 * cosseno, então a busca por similaridade funciona de verdade nos testes.</p>
 *
 * <p>A resposta repete quantos documentos recebeu, para os testes conferirem
 * que o contexto chegou ao modelo. Diante do prompt de sugestão de trilhas
 * (issue #63), responde o JSON que aquele fluxo espera, escolhendo as
 * primeiras trilhas do catálogo recebido — assim o endpoint funciona de ponta
 * a ponta com {@code app.ia.provedor=fake}, sem rede.</p>
 */
public class ClienteIaFake implements ClienteIa {

    public static final String PREFIXO_RESPOSTA = "Resposta simulada";

    public static final String MOTIVO_SIMULADO = "Sugestão simulada a partir do catálogo recebido.";

    private static final Pattern ID_DE_TRILHA = Pattern.compile("<trilha id=\"([^\"]+)\"");

    private static final Pattern LIMITE_PEDIDO = Pattern.compile("Indique no máximo (\\d+) trilhas");

    public static final String TEXTO_RASCUNHO_SIMULADO = "Rascunho simulado: revise os campos antes de salvar.";

    private static final Pattern PERGUNTA = Pattern.compile("<pergunta>\\s*(.*?)\\s*</pergunta>", Pattern.DOTALL);

    private static final Pattern PEDIDO_DE_TRILHA =
            Pattern.compile("(cri|mont|planej|nova trilha|rascunho)", Pattern.CASE_INSENSITIVE);

    private final int dimensoes;

    public ClienteIaFake(int dimensoes) {
        this.dimensoes = dimensoes;
    }

    @Override
    public RespostaIa gerarResposta(String promptSistema, List<MensagemIa> mensagens) {
        String ultima = mensagens.isEmpty() ? "" : mensagens.get(mensagens.size() - 1).texto();
        // A ajuda também manda o catálogo; o pedido em <pergunta> é o que a distingue.
        if (ultima.contains("<pergunta>")) {
            return new RespostaIa(ajudaComTrilhas(ultima), 0, 0);
        }
        if (ultima.contains("<catalogo>")) {
            return new RespostaIa(sugestoesDoCatalogo(ultima), 0, 0);
        }
        int documentos = ultima.split("<documento ", -1).length - 1;
        return new RespostaIa(PREFIXO_RESPOSTA + " com base em " + documentos + " trecho(s).", 0, 0);
    }

    /**
     * JSON da ajuda nas trilhas: pedido para criar/montar uma trilha devolve um
     * rascunho com o próprio pedido no nome; qualquer outro, só o texto.
     */
    private static String ajudaComTrilhas(String turno) {
        Matcher pergunta = PERGUNTA.matcher(turno);
        String pedido = pergunta.find() ? pergunta.group(1).strip() : "";
        if (!PEDIDO_DE_TRILHA.matcher(pedido).find()) {
            return "{\"texto\":\"" + json(PREFIXO_RESPOSTA + " para: " + pedido) + "\",\"rascunho\":null}";
        }
        String tema = pedido.length() <= 80 ? pedido : pedido.substring(0, 80).strip();
        return "{\"texto\":\"" + json(TEXTO_RASCUNHO_SIMULADO) + "\",\"rascunho\":{"
                + "\"nome\":\"" + json("Trilha: " + tema) + "\","
                + "\"descricao\":\"" + json("Rascunho simulado a partir do pedido: " + pedido) + "\","
                + "\"objetivos\":\"Entender o tema; aplicar no dia a dia; passar na avaliação\","
                + "\"prazoDias\":30,\"area\":null}}";
    }

    private static String json(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    /** JSON com as primeiras trilhas do catálogo, respeitando o limite pedido no prompt. */
    private static String sugestoesDoCatalogo(String turno) {
        Matcher limite = LIMITE_PEDIDO.matcher(turno);
        int maximo = limite.find() ? Integer.parseInt(limite.group(1)) : 5;
        StringBuilder json = new StringBuilder("{\"sugestoes\":[");
        Matcher ids = ID_DE_TRILHA.matcher(turno);
        int escolhidas = 0;
        while (escolhidas < maximo && ids.find()) {
            if (escolhidas > 0) {
                json.append(',');
            }
            json.append("{\"id\":\"").append(ids.group(1))
                    .append("\",\"motivo\":\"").append(MOTIVO_SIMULADO).append("\"}");
            escolhidas++;
        }
        return json.append("]}").toString();
    }

    @Override
    public List<float[]> gerarEmbeddings(List<String> textos, FinalidadeEmbedding finalidade) {
        return textos.stream().map(this::vetorDe).toList();
    }

    private float[] vetorDe(String texto) {
        float[] vetor = new float[dimensoes];
        String normalizado = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        for (String palavra : normalizado.split("[^a-z0-9]+")) {
            if (palavra.length() >= 3) {
                vetor[Math.floorMod(palavra.hashCode(), dimensoes)] += 1f;
            }
        }
        double norma = 0;
        for (float valor : vetor) {
            norma += valor * valor;
        }
        if (norma == 0) {
            // Texto sem palavras: vetor fixo, para nunca gravar vetor nulo (cosseno indefinido).
            vetor[0] = 1f;
            return vetor;
        }
        float fator = (float) (1 / Math.sqrt(norma));
        for (int i = 0; i < vetor.length; i++) {
            vetor[i] *= fator;
        }
        return vetor;
    }
}
