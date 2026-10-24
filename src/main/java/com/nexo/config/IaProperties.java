package com.nexo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Assistente de IA lido de {@code app.ia.*}.
 *
 * <p>A chave nunca tem default: sem {@code IA_API_KEY} a aplicação sobe, e só
 * as chamadas de IA respondem 503.</p>
 *
 * @param provedor        {@code gemini} (real) ou {@code fake} (testes)
 * @param apiKey          chave do provedor — nunca vai para log nem para resposta
 * @param urlBase         endereço base da API do provedor
 * @param modelo          modelo de chat
 * @param modeloEmbedding modelo de embeddings
 * @param dimensoes       tamanho do vetor; precisa bater com a coluna {@code vector(768)}
 * @param timeout         tempo máximo de cada chamada
 * @param maxTentativas   tentativas por chamada em timeout, 429 ou erro 5xx
 * @param indexacao       divisão dos documentos em trechos e nova tentativa
 * @param busca           quantos trechos buscar e o quão parecidos precisam ser
 * @param consulta        limites do endpoint de perguntas
 */
@ConfigurationProperties(prefix = "app.ia")
public record IaProperties(
        String provedor,
        String apiKey,
        String urlBase,
        String modelo,
        String modeloEmbedding,
        Integer dimensoes,
        Duration timeout,
        Integer maxTentativas,
        Indexacao indexacao,
        Busca busca,
        Consulta consulta) {

    public static final int DIMENSOES_PADRAO = 768;

    public IaProperties {
        provedor = textoOu(provedor, "gemini");
        urlBase = textoOu(urlBase, "https://generativelanguage.googleapis.com/v1beta");
        modelo = textoOu(modelo, "gemini-2.5-flash");
        modeloEmbedding = textoOu(modeloEmbedding, "gemini-embedding-001");
        dimensoes = dimensoes == null || dimensoes <= 0 ? DIMENSOES_PADRAO : dimensoes;
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(20) : timeout;
        maxTentativas = maxTentativas == null || maxTentativas < 1 ? 3 : maxTentativas;
        indexacao = indexacao == null ? new Indexacao(null, null, null, null) : indexacao;
        busca = busca == null ? new Busca(null, null) : busca;
        consulta = consulta == null ? new Consulta(null, null) : consulta;
    }

    public boolean temChave() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * @param tamanhoTrecho          máximo de caracteres por trecho
     * @param sobreposicao           caracteres repetidos entre um trecho e o seguinte
     * @param maxTentativas          tentativas de indexar um documento antes de desistir
     * @param intervaloNovaTentativa espera base entre uma falha e a próxima tentativa
     */
    public record Indexacao(Integer tamanhoTrecho, Integer sobreposicao, Integer maxTentativas,
                            Duration intervaloNovaTentativa) {

        public Indexacao {
            tamanhoTrecho = tamanhoTrecho == null || tamanhoTrecho < 100 ? 1000 : tamanhoTrecho;
            sobreposicao = sobreposicao == null || sobreposicao < 0 ? 200 : Math.min(sobreposicao, tamanhoTrecho / 2);
            maxTentativas = maxTentativas == null || maxTentativas < 1 ? 5 : maxTentativas;
            intervaloNovaTentativa = intervaloNovaTentativa == null || intervaloNovaTentativa.isNegative()
                    ? Duration.ofMinutes(5) : intervaloNovaTentativa;
        }
    }

    /**
     * @param trechos         quantos trechos entram no prompt
     * @param distanciaMaxima distância de cosseno máxima (0 = idêntico, 2 = oposto) para um trecho contar como relevante
     */
    public record Busca(Integer trechos, Double distanciaMaxima) {

        public Busca {
            trechos = trechos == null || trechos < 1 ? 5 : trechos;
            distanciaMaxima = distanciaMaxima == null || distanciaMaxima <= 0 ? 0.6 : distanciaMaxima;
        }
    }

    /**
     * @param perguntasPorMinuto  perguntas aceitas por usuário a cada minuto
     * @param mensagensHistorico  mensagens anteriores da sessão que entram no prompt
     */
    public record Consulta(Integer perguntasPorMinuto, Integer mensagensHistorico) {

        public Consulta {
            perguntasPorMinuto = perguntasPorMinuto == null || perguntasPorMinuto < 1 ? 10 : perguntasPorMinuto;
            mensagensHistorico = mensagensHistorico == null || mensagensHistorico < 0 ? 6 : mensagensHistorico;
        }
    }

    private static String textoOu(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
