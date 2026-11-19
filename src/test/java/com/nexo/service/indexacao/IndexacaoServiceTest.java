package com.nexo.service.indexacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.config.IaProperties;
import com.nexo.entity.Conteudo;
import com.nexo.entity.ConteudoChunk;
import com.nexo.entity.IndexacaoConteudo;
import com.nexo.entity.StatusConteudo;
import com.nexo.entity.StatusIndexacao;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoConteudo;
import com.nexo.exception.IaIndisponivelException;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.ClienteIaFake;
import com.nexo.ia.FinalidadeEmbedding;
import com.nexo.repository.ConteudoChunkRepository;
import com.nexo.repository.ConteudoRepository;
import com.nexo.repository.IndexacaoConteudoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class IndexacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");

    @Mock
    private ConteudoRepository conteudoRepository;

    @Mock
    private ConteudoChunkRepository chunkRepository;

    @Mock
    private IndexacaoConteudoRepository indexacaoRepository;

    @Mock
    private ClienteIa clienteIa;

    @Mock
    private PlatformTransactionManager transactionManager;

    private IndexacaoService service;
    private Conteudo conteudo;

    @BeforeEach
    void prepara() {
        org.mockito.Mockito.lenient().when(clienteIa.disponivel()).thenReturn(true);
        IaProperties properties = new IaProperties("fake", null, null, null, null, 768, null, null,
                new IaProperties.Indexacao(100, 20, 3, Duration.ofMinutes(5)), null, null, null);
        service = new IndexacaoService(conteudoRepository, chunkRepository, indexacaoRepository,
                List.of(new CamposDoConteudo()), new DivisorDeTexto(properties), clienteIa, transactionManager,
                properties, Clock.fixed(AGORA, ZoneOffset.UTC));

        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        conteudo = new Conteudo();
        ReflectionTestUtils.setField(conteudo, "id", UUID.randomUUID());
        conteudo.setTenant(tenant);
        conteudo.setTitulo("Política de férias");
        conteudo.setTipo(TipoConteudo.TEXTO);
        conteudo.setCorpo("Cada colaborador tem trinta dias de férias por ano. ".repeat(6));
        conteudo.setStatus(StatusConteudo.PUBLICADO);
    }

    @Test
    void publicadoGeraTrechosEmOrdemEMarcaIndexado() {
        when(conteudoRepository.findById(conteudo.getId())).thenReturn(Optional.of(conteudo));
        when(clienteIa.gerarEmbeddings(anyList(), any()))
                .thenAnswer(chamada -> new ClienteIaFake(768).gerarEmbeddings(chamada.getArgument(0),
                        FinalidadeEmbedding.DOCUMENTO));
        when(indexacaoRepository.findById(conteudo.getId())).thenReturn(Optional.empty());

        service.indexar(conteudo.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ConteudoChunk>> chunks = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(chunks.capture());
        assertThat(chunks.getValue()).hasSizeGreaterThan(1);
        assertThat(chunks.getValue()).extracting(ConteudoChunk::getOrdem)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, chunks.getValue().size())
                        .boxed().toList());
        assertThat(chunks.getValue()).allSatisfy(chunk -> {
            assertThat(chunk.getTenantId()).isEqualTo(conteudo.getTenant().getId());
            assertThat(chunk.getIndexadoEm()).isEqualTo(OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC));
            assertThat(chunk.getEmbedding()).hasSize(768);
        });
        assertThat(chunks.getValue().get(0).getTexto()).startsWith("Política de férias");

        ArgumentCaptor<IndexacaoConteudo> indexacao = ArgumentCaptor.forClass(IndexacaoConteudo.class);
        verify(indexacaoRepository).save(indexacao.capture());
        assertThat(indexacao.getValue().getStatus()).isEqualTo(StatusIndexacao.INDEXADO);
        verify(clienteIa).gerarEmbeddings(anyList(), org.mockito.ArgumentMatchers.eq(FinalidadeEmbedding.DOCUMENTO));
    }

    @Test
    void erroDoProvedorRegistraFalhaComNovaTentativaSemLancar() {
        when(conteudoRepository.findById(conteudo.getId())).thenReturn(Optional.of(conteudo));
        when(clienteIa.gerarEmbeddings(anyList(), any())).thenThrow(new IaIndisponivelException("fora do ar"));
        when(indexacaoRepository.findById(conteudo.getId())).thenReturn(Optional.empty());

        service.indexar(conteudo.getId());

        verify(chunkRepository, never()).saveAll(anyList());
        ArgumentCaptor<IndexacaoConteudo> indexacao = ArgumentCaptor.forClass(IndexacaoConteudo.class);
        verify(indexacaoRepository).save(indexacao.capture());
        assertThat(indexacao.getValue().getStatus()).isEqualTo(StatusIndexacao.FALHOU);
        assertThat(indexacao.getValue().getTentativas()).isEqualTo(1);
        assertThat(indexacao.getValue().getUltimoErro()).contains("IaIndisponivelException");
        assertThat(indexacao.getValue().getProximaTentativaEm())
                .isEqualTo(OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC).plusMinutes(5));
    }

    @Test
    void ultimaTentativaNaoAgendaOutra() {
        IndexacaoConteudo jaFalhou = IndexacaoConteudo.de(conteudo.getId(), conteudo.getTenant().getId(),
                OffsetDateTime.now());
        jaFalhou.marcarFalha("x", OffsetDateTime.now(), null);
        jaFalhou.marcarFalha("x", OffsetDateTime.now(), null);
        when(conteudoRepository.findById(conteudo.getId())).thenReturn(Optional.of(conteudo));
        when(clienteIa.gerarEmbeddings(anyList(), any())).thenThrow(new IaIndisponivelException("fora do ar"));
        when(indexacaoRepository.findById(conteudo.getId())).thenReturn(Optional.of(jaFalhou));

        service.indexar(conteudo.getId());

        assertThat(jaFalhou.getTentativas()).isEqualTo(3);
        assertThat(jaFalhou.getProximaTentativaEm()).isNull();
    }

    @Test
    void conteudoEmRascunhoTemOsTrechosRemovidos() {
        conteudo.setStatus(StatusConteudo.RASCUNHO);
        when(conteudoRepository.findById(conteudo.getId())).thenReturn(Optional.of(conteudo));

        service.indexar(conteudo.getId());

        verify(chunkRepository).apagarDoConteudo(conteudo.getId());
        verify(clienteIa, never()).gerarEmbeddings(anyList(), any());
    }

    @Test
    void despublicadoDuranteAIndexacaoNaoGravaTrechos() {
        Conteudo despublicado = new Conteudo();
        despublicado.setStatus(StatusConteudo.RASCUNHO);
        when(conteudoRepository.findById(conteudo.getId()))
                .thenReturn(Optional.of(conteudo))
                .thenReturn(Optional.of(despublicado));
        when(clienteIa.gerarEmbeddings(anyList(), any()))
                .thenAnswer(chamada -> new ClienteIaFake(768).gerarEmbeddings(chamada.getArgument(0),
                        FinalidadeEmbedding.DOCUMENTO));

        service.indexar(conteudo.getId());

        verify(chunkRepository).apagarDoConteudo(conteudo.getId());
        verify(chunkRepository, never()).saveAll(anyList());
    }

    @Test
    void reprocessaSoAsFalhasVencidas() {
        IndexacaoConteudo falha = IndexacaoConteudo.de(conteudo.getId(), conteudo.getTenant().getId(),
                OffsetDateTime.now());
        when(indexacaoRepository.findByStatusAndTentativasLessThanAndProximaTentativaEmLessThanEqual(
                any(), org.mockito.ArgumentMatchers.eq(3), any())).thenReturn(List.of(falha));
        when(conteudoRepository.findById(conteudo.getId())).thenReturn(Optional.of(conteudo));
        when(clienteIa.gerarEmbeddings(anyList(), any()))
                .thenAnswer(chamada -> new ClienteIaFake(768).gerarEmbeddings(chamada.getArgument(0),
                        FinalidadeEmbedding.DOCUMENTO));
        when(indexacaoRepository.findById(conteudo.getId())).thenReturn(Optional.of(falha));

        int reprocessadas = service.reprocessarFalhas();

        assertThat(reprocessadas).isEqualTo(1);
        assertThat(falha.getStatus()).isEqualTo(StatusIndexacao.INDEXADO);
        assertThat(falha.getTentativas()).isZero();
    }
}
