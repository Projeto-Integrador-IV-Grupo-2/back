package com.nexo.service.indexacao;

import com.nexo.config.IaProperties;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.FinalidadeEmbedding;
import com.nexo.repository.ConteudoChunkRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Busca semântica nos trechos indexados (issue #58).
 *
 * <p>Só devolve trechos do tenant informado e de conteúdos publicados — o
 * filtro está na consulta de {@link ConteudoChunkRepository#buscarParecidos}.
 * Trechos mais distantes que {@code app.ia.busca.distancia-maxima} são
 * descartados: é isso que permite ao assistente dizer "não encontrei" em vez
 * de responder com material sem relação.</p>
 *
 * <p>Singleton sem estado. Sem {@code @Transactional}: a geração do embedding
 * da pergunta não deve segurar conexão do banco.</p>
 */
@Service
public class BuscaDeTrechosService {

    private final ClienteIa clienteIa;
    private final ConteudoChunkRepository chunkRepository;
    private final double distanciaMaxima;

    public BuscaDeTrechosService(ClienteIa clienteIa, ConteudoChunkRepository chunkRepository,
                                 IaProperties properties) {
        this.clienteIa = clienteIa;
        this.chunkRepository = chunkRepository;
        this.distanciaMaxima = properties.busca().distanciaMaxima();
    }

    /**
     * @param limite número máximo de trechos
     * @return os trechos relevantes, do mais para o menos parecido; vazio quando nada for relevante
     */
    public List<TrechoEncontrado> buscar(UUID tenantId, String texto, int limite) {
        return buscar(tenantId, texto, limite, false);
    }

    /**
     * @param incluirRestritos se entram os conteúdos visíveis só para a gestão
     *                         (só quando quem pergunta é ADMIN ou GESTOR)
     */
    public List<TrechoEncontrado> buscar(UUID tenantId, String texto, int limite, boolean incluirRestritos) {
        if (texto == null || texto.isBlank() || limite < 1) {
            return List.of();
        }
        float[] vetor = clienteIa.gerarEmbedding(texto, FinalidadeEmbedding.CONSULTA);
        String literal = literal(vetor);
        var porVetor = (incluirRestritos
                ? chunkRepository.buscarParecidos(tenantId, literal, limite, true)
                : chunkRepository.buscarParecidos(tenantId, literal, limite))
                .stream()
                .filter(trecho -> trecho.getDistancia() != null && trecho.getDistancia() <= distanciaMaxima)
                .toList();

        String consulta = consultaTextual(texto);
        List<ConteudoChunkRepository.TrechoProjecao> porPalavras = consulta.isEmpty()
                ? List.of()
                : chunkRepository.buscarPorPalavras(tenantId, consulta, literal, limite, incluirRestritos);
        if (porPalavras == null) {
            porPalavras = List.of();
        }

        return combinar(porVetor, porPalavras, limite).stream()
                .map(trecho -> new TrechoEncontrado(trecho.getChunkId(), trecho.getConteudoId(), trecho.getTitulo(),
                        trecho.getOrdem(), trecho.getTexto(),
                        trecho.getDistancia() == null ? 1.0 : trecho.getDistancia()))
                .toList();
    }

    /** Constante da fusão por posição (Reciprocal Rank Fusion); 60 é o valor usual. */
    private static final int K_FUSAO = 60;

    /**
     * Junta as duas listas pela posição de cada trecho nelas (RRF): quem
     * aparece bem nas duas sobe; sem resultado textual, a ordem do vetor fica igual.
     */
    static List<ConteudoChunkRepository.TrechoProjecao> combinar(
            List<ConteudoChunkRepository.TrechoProjecao> porVetor,
            List<ConteudoChunkRepository.TrechoProjecao> porPalavras, int limite) {
        java.util.Map<UUID, Double> pontos = new java.util.LinkedHashMap<>();
        java.util.Map<UUID, ConteudoChunkRepository.TrechoProjecao> porId = new java.util.HashMap<>();
        for (int i = 0; i < porVetor.size(); i++) {
            var trecho = porVetor.get(i);
            porId.putIfAbsent(trecho.getChunkId(), trecho);
            pontos.merge(trecho.getChunkId(), 1.0 / (K_FUSAO + i + 1), Double::sum);
        }
        for (int i = 0; i < porPalavras.size(); i++) {
            var trecho = porPalavras.get(i);
            porId.putIfAbsent(trecho.getChunkId(), trecho);
            pontos.merge(trecho.getChunkId(), 1.0 / (K_FUSAO + i + 1), Double::sum);
        }
        return pontos.entrySet().stream()
                .sorted(java.util.Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limite)
                .map(entrada -> porId.get(entrada.getKey()))
                .toList();
    }

    /**
     * Palavras da pergunta (3+ letras) unidas por OU, no formato do
     * {@code to_tsquery}. Só letras e números entram, então nada da pergunta
     * vira operador da consulta. Palavras vazias ("como", "para") o próprio
     * dicionário {@code portuguese} descarta.
     */
    static String consultaTextual(String texto) {
        return java.util.Arrays.stream(texto.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(palavra -> palavra.length() >= 3)
                .distinct()
                .limit(20)
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    /** Formato de texto do pgvector: {@code [0.1,0.2,...]}. */
    static String literal(float[] vetor) {
        StringBuilder texto = new StringBuilder(vetor.length * 10).append('[');
        for (int i = 0; i < vetor.length; i++) {
            if (i > 0) {
                texto.append(',');
            }
            texto.append(Float.toString(vetor[i]));
        }
        return texto.append(']').toString();
    }
}
