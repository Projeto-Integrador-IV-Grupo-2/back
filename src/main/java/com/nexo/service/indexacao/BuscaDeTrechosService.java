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
        if (texto == null || texto.isBlank() || limite < 1) {
            return List.of();
        }
        float[] vetor = clienteIa.gerarEmbedding(texto, FinalidadeEmbedding.CONSULTA);
        return chunkRepository.buscarParecidos(tenantId, literal(vetor), limite).stream()
                .filter(trecho -> trecho.getDistancia() != null && trecho.getDistancia() <= distanciaMaxima)
                .map(trecho -> new TrechoEncontrado(trecho.getChunkId(), trecho.getConteudoId(), trecho.getTitulo(),
                        trecho.getOrdem(), trecho.getTexto(), trecho.getDistancia()))
                .toList();
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
