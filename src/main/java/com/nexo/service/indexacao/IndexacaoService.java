package com.nexo.service.indexacao;

import com.nexo.config.IaProperties;
import com.nexo.entity.Conteudo;
import com.nexo.entity.ConteudoChunk;
import com.nexo.entity.IndexacaoConteudo;
import com.nexo.entity.StatusIndexacao;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.FinalidadeEmbedding;
import com.nexo.repository.ConteudoChunkRepository;
import com.nexo.repository.ConteudoRepository;
import com.nexo.repository.IndexacaoConteudoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Indexação dos conteúdos para o assistente de IA (issue #58): texto →
 * trechos → embeddings → {@code conteudo_chunk}.
 *
 * <p><strong>Nunca lança exceção.</strong> Arquivo ilegível ou provedor fora
 * do ar vira falha registrada em {@code indexacao_conteudo}, com nova
 * tentativa agendada — a publicação do documento não é afetada.</p>
 *
 * <p>A chamada ao provedor fica fora de transação, para não segurar conexão
 * do banco enquanto espera a rede. A gravação dos trechos é uma transação
 * curta que relê o status: se o conteúdo foi despublicado no meio do caminho,
 * os trechos são removidos em vez de gravados.</p>
 *
 * <p>Nenhum log carrega o texto do documento.</p>
 */
@Service
public class IndexacaoService {

    private static final Logger log = LoggerFactory.getLogger(IndexacaoService.class);

    /** Teto da espera entre tentativas, para o backoff exponencial não crescer sem fim. */
    private static final Duration ESPERA_MAXIMA = Duration.ofHours(6);

    private final ConteudoRepository conteudoRepository;
    private final ConteudoChunkRepository chunkRepository;
    private final IndexacaoConteudoRepository indexacaoRepository;
    private final List<FonteDeTextoDoConteudo> fontes;
    private final DivisorDeTexto divisor;
    private final ClienteIa clienteIa;
    private final TransactionTemplate transacao;
    private final IaProperties.Indexacao config;
    private final Clock clock;

    public IndexacaoService(ConteudoRepository conteudoRepository,
                            ConteudoChunkRepository chunkRepository,
                            IndexacaoConteudoRepository indexacaoRepository,
                            List<FonteDeTextoDoConteudo> fontes,
                            DivisorDeTexto divisor,
                            ClienteIa clienteIa,
                            PlatformTransactionManager transactionManager,
                            IaProperties properties,
                            Clock clock) {
        this.conteudoRepository = conteudoRepository;
        this.chunkRepository = chunkRepository;
        this.indexacaoRepository = indexacaoRepository;
        this.fontes = fontes;
        this.divisor = divisor;
        this.clienteIa = clienteIa;
        this.transacao = new TransactionTemplate(transactionManager);
        this.config = properties.indexacao();
        this.clock = clock;
    }

    /** Indexa (ou reindexa) o conteúdo. Conteúdo que não está publicado tem os trechos removidos. */
    public void indexar(UUID conteudoId) {
        Material material = null;
        try {
            material = transacao.execute(status -> lerMaterial(conteudoId));
            if (material == null) {
                remover(conteudoId);
                return;
            }
            List<String> trechos = divisor.dividir(material.texto());
            List<float[]> embeddings = trechos.isEmpty()
                    ? List.of()
                    : clienteIa.gerarEmbeddings(trechos, FinalidadeEmbedding.DOCUMENTO);
            if (embeddings.size() != trechos.size()) {
                throw new IllegalStateException("Quantidade de embeddings diferente da de trechos");
            }
            UUID tenantId = material.tenantId();
            transacao.executeWithoutResult(status -> gravar(conteudoId, tenantId, trechos, embeddings));
        } catch (RuntimeException ex) {
            registrarFalha(conteudoId, material == null ? null : material.tenantId(), ex);
        }
    }

    /** Remove os trechos e a situação da indexação do conteúdo. */
    public void remover(UUID conteudoId) {
        try {
            transacao.executeWithoutResult(status -> {
                chunkRepository.apagarDoConteudo(conteudoId);
                indexacaoRepository.findById(conteudoId).ifPresent(indexacaoRepository::delete);
            });
        } catch (RuntimeException ex) {
            log.warn("Falha ao remover os trechos do conteúdo {}: {}", conteudoId, ex.getClass().getSimpleName());
        }
    }

    /** Tenta de novo as falhas cuja hora chegou. Devolve quantas foram reprocessadas. */
    public int reprocessarFalhas() {
        List<UUID> pendentes = transacao.execute(status -> indexacaoRepository
                .findByStatusAndTentativasLessThanAndProximaTentativaEmLessThanEqual(
                        StatusIndexacao.FALHOU, config.maxTentativas(), agora())
                .stream()
                .map(IndexacaoConteudo::getConteudoId)
                .toList());
        pendentes.forEach(this::indexar);
        return pendentes.size();
    }

    private Material lerMaterial(UUID conteudoId) {
        Conteudo conteudo = conteudoRepository.findById(conteudoId).orElse(null);
        if (conteudo == null || !conteudo.isPublicado()) {
            return null;
        }
        String texto = fontes.stream()
                .map(fonte -> fonte.textoDe(conteudo))
                .filter(Objects::nonNull)
                .filter(parte -> !parte.isBlank())
                .collect(Collectors.joining("\n\n"));
        return new Material(conteudo.getTenant().getId(), texto);
    }

    private void gravar(UUID conteudoId, UUID tenantId, List<String> trechos, List<float[]> embeddings) {
        chunkRepository.apagarDoConteudo(conteudoId);
        boolean aindaPublicado = conteudoRepository.findById(conteudoId).map(Conteudo::isPublicado).orElse(false);
        if (!aindaPublicado) {
            indexacaoRepository.findById(conteudoId).ifPresent(indexacaoRepository::delete);
            return;
        }
        OffsetDateTime agora = agora();
        List<ConteudoChunk> chunks = new ArrayList<>(trechos.size());
        for (int i = 0; i < trechos.size(); i++) {
            chunks.add(ConteudoChunk.de(tenantId, conteudoId, i + 1, trechos.get(i), embeddings.get(i), agora));
        }
        chunkRepository.saveAll(chunks);
        IndexacaoConteudo indexacao = indexacaoRepository.findById(conteudoId)
                .orElseGet(() -> IndexacaoConteudo.de(conteudoId, tenantId, agora));
        indexacao.marcarIndexado(agora);
        indexacaoRepository.save(indexacao);
    }

    private void registrarFalha(UUID conteudoId, UUID tenantIdLido, RuntimeException erro) {
        try {
            transacao.executeWithoutResult(status -> {
                UUID tenantId = tenantIdLido != null ? tenantIdLido
                        : conteudoRepository.findById(conteudoId).map(c -> c.getTenant().getId()).orElse(null);
                if (tenantId == null) {
                    return; // conteúdo não existe mais: nada a registrar
                }
                OffsetDateTime agora = agora();
                IndexacaoConteudo indexacao = indexacaoRepository.findById(conteudoId)
                        .orElseGet(() -> IndexacaoConteudo.de(conteudoId, tenantId, agora));
                int tentativa = indexacao.getTentativas() + 1;
                OffsetDateTime proxima = tentativa < config.maxTentativas() ? agora.plus(espera(tentativa)) : null;
                indexacao.marcarFalha(descrever(erro), agora, proxima);
                indexacaoRepository.save(indexacao);
                log.warn("Falha ao indexar o conteúdo {} (tentativa {} de {}): {}",
                        conteudoId, tentativa, config.maxTentativas(), erro.getClass().getSimpleName());
            });
        } catch (RuntimeException ex) {
            log.error("Não foi possível registrar a falha de indexação do conteúdo {}", conteudoId, ex);
        }
    }

    /** Espera dobrando a cada tentativa: intervalo, 2×, 4×... até {@link #ESPERA_MAXIMA}. */
    private Duration espera(int tentativa) {
        Duration espera = config.intervaloNovaTentativa().multipliedBy(1L << Math.min(tentativa - 1, 10));
        return espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera;
    }

    private static String descrever(RuntimeException erro) {
        String mensagem = erro.getMessage();
        return erro.getClass().getSimpleName() + (mensagem == null ? "" : ": " + mensagem);
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(clock);
    }

    private record Material(UUID tenantId, String texto) {
    }
}
