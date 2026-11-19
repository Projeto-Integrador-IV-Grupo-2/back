package com.nexo.ia;

import java.util.List;

/**
 * Único ponto de acesso ao modelo de IA (issue #53).
 *
 * <p>Nenhuma outra parte do sistema conhece o provedor: trocar de provedor
 * exige só uma nova implementação desta interface e a configuração em
 * {@code app.ia.provedor} — ver {@link ClienteIaFactory}.</p>
 *
 * <p>Toda falha do provedor (sem chave, timeout, limite de uso, erro dele)
 * vira {@link com.nexo.exception.IaIndisponivelException}, que a API responde
 * com 503.</p>
 */
public interface ClienteIa {

    /**
     * Gera a resposta do modelo.
     *
     * @param promptSistema instruções fixas do assistente
     * @param mensagens     conversa em ordem cronológica; a última é a pergunta atual
     */
    RespostaIa gerarResposta(String promptSistema, List<MensagemIa> mensagens);

    /**
     * Gera um embedding por texto, na mesma ordem recebida.
     *
     * @param finalidade documento indexado ou pergunta do usuário — alguns
     *                   provedores otimizam o vetor para cada lado da busca
     */
    List<float[]> gerarEmbeddings(List<String> textos, FinalidadeEmbedding finalidade);

    /**
     * {@code false} quando o provedor nem está configurado (sem chave): chamar
     * só renderia {@link com.nexo.exception.IaIndisponivelException}. Quem
     * trabalha em segundo plano, como a indexação, usa isto para esperar a
     * configuração em vez de registrar falha atrás de falha.
     */
    default boolean disponivel() {
        return true;
    }

    /** Atalho para um texto só. */
    default float[] gerarEmbedding(String texto, FinalidadeEmbedding finalidade) {
        return gerarEmbeddings(List.of(texto), finalidade).get(0);
    }
}
