package com.nexo.service.indexacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexo.config.IaProperties;
import com.nexo.ia.ClienteIaFake;
import com.nexo.ia.FinalidadeEmbedding;
import com.nexo.repository.ConteudoChunkRepository;
import com.nexo.repository.ConteudoChunkRepository.TrechoProjecao;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Busca de trechos com o {@link ClienteIaFake}: o embedding da pergunta é o
 * real do fake; o banco (onde ficam os filtros de tenant e de publicado) é
 * simulado. O filtro na consulta SQL é coberto no {@code IndexacaoIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class BuscaDeTrechosServiceTest {

    private static final double DISTANCIA_MAXIMA = 0.6;

    @Mock
    private ConteudoChunkRepository chunkRepository;

    private ClienteIaFake clienteIa;
    private BuscaDeTrechosService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void prepara() {
        clienteIa = spy(new ClienteIaFake(IaProperties.DIMENSOES_PADRAO));
        IaProperties properties = new IaProperties("fake", null, null, null, null, null, null, null, null,
                new IaProperties.Busca(5, DISTANCIA_MAXIMA), null, null);
        service = new BuscaDeTrechosService(clienteIa, chunkRepository, properties);
    }

    @Test
    void buscaNoTenantComOEmbeddingDaPerguntaComoConsulta() {
        when(chunkRepository.buscarParecidos(eq(tenantId), anyString(), eq(3))).thenReturn(List.of());

        service.buscar(tenantId, "Quantos dias de férias eu tenho?", 3);

        ArgumentCaptor<String> vetor = ArgumentCaptor.forClass(String.class);
        verify(chunkRepository).buscarParecidos(eq(tenantId), vetor.capture(), eq(3));
        verify(clienteIa).gerarEmbedding("Quantos dias de férias eu tenho?", FinalidadeEmbedding.CONSULTA);
        float[] esperado = clienteIa.gerarEmbedding("Quantos dias de férias eu tenho?", FinalidadeEmbedding.CONSULTA);
        assertThat(vetor.getValue()).isEqualTo(BuscaDeTrechosService.literal(esperado));
    }

    @Test
    void devolveOsTrechosNaOrdemDoBancoComOsCamposPreenchidos() {
        UUID conteudoId = UUID.randomUUID();
        TrechoProjecao maisParecido = trecho(conteudoId, "Política de férias", 2, "Trinta dias por ano.", 0.1);
        TrechoProjecao menosParecido = trecho(conteudoId, "Política de férias", 1, "Pedido pelo portal.", 0.4);
        when(chunkRepository.buscarParecidos(eq(tenantId), anyString(), eq(5)))
                .thenReturn(List.of(maisParecido, menosParecido));

        List<TrechoEncontrado> encontrados = service.buscar(tenantId, "férias", 5);

        assertThat(encontrados).containsExactly(
                new TrechoEncontrado(maisParecido.getChunkId(), conteudoId, "Política de férias", 2,
                        "Trinta dias por ano.", 0.1),
                new TrechoEncontrado(menosParecido.getChunkId(), conteudoId, "Política de férias", 1,
                        "Pedido pelo portal.", 0.4));
    }

    @Test
    void descartaTrechosMaisDistantesQueOLimiteOuSemDistancia() {
        TrechoProjecao relevante = trecho(UUID.randomUUID(), "Férias", 1, "Trinta dias.", DISTANCIA_MAXIMA);
        TrechoProjecao distante = trecho(UUID.randomUUID(), "Cantina", 1, "Cardápio da semana.", 0.61);
        TrechoProjecao semDistancia = trecho(UUID.randomUUID(), "Outro", 1, "Texto.", null);
        when(chunkRepository.buscarParecidos(eq(tenantId), anyString(), eq(5)))
                .thenReturn(List.of(relevante, distante, semDistancia));

        List<TrechoEncontrado> encontrados = service.buscar(tenantId, "férias", 5);

        assertThat(encontrados).extracting(TrechoEncontrado::chunkId).containsExactly(relevante.getChunkId());
    }

    @Test
    void nadaRelevanteDevolveListaVazia() {
        when(chunkRepository.buscarParecidos(eq(tenantId), anyString(), eq(5)))
                .thenReturn(List.of(trecho(UUID.randomUUID(), "Cantina", 1, "Cardápio.", 1.2)));

        assertThat(service.buscar(tenantId, "férias", 5)).isEmpty();
    }

    @Test
    void textoVazioOuLimiteInvalidoNaoChamaProvedorNemBanco() {
        assertThat(service.buscar(tenantId, null, 5)).isEmpty();
        assertThat(service.buscar(tenantId, "   ", 5)).isEmpty();
        assertThat(service.buscar(tenantId, "férias", 0)).isEmpty();

        verify(clienteIa, never()).gerarEmbedding(anyString(), any());
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void cadaBuscaChegaAoBancoComOTenantDeQuemPerguntou() {
        UUID outroTenant = UUID.randomUUID();
        when(chunkRepository.buscarParecidos(any(), anyString(), anyInt())).thenReturn(List.of());

        service.buscar(tenantId, "férias", 5);
        service.buscar(outroTenant, "férias", 5);

        ArgumentCaptor<UUID> tenants = ArgumentCaptor.forClass(UUID.class);
        verify(chunkRepository, times(2)).buscarParecidos(tenants.capture(), anyString(), anyInt());
        assertThat(tenants.getAllValues()).containsExactly(tenantId, outroTenant);
    }

    @Test
    void literalNoFormatoDoPgvector() {
        assertThat(BuscaDeTrechosService.literal(new float[] {0.5f, -1.0f, 0.25f})).isEqualTo("[0.5,-1.0,0.25]");
        assertThat(BuscaDeTrechosService.literal(new float[0])).isEqualTo("[]");
    }

    private static TrechoProjecao trecho(UUID conteudoId, String titulo, int ordem, String texto, Double distancia) {
        UUID chunkId = UUID.randomUUID();
        return new TrechoProjecao() {
            @Override
            public UUID getChunkId() {
                return chunkId;
            }

            @Override
            public UUID getConteudoId() {
                return conteudoId;
            }

            @Override
            public String getTitulo() {
                return titulo;
            }

            @Override
            public Integer getOrdem() {
                return ordem;
            }

            @Override
            public String getTexto() {
                return texto;
            }

            @Override
            public Double getDistancia() {
                return distancia;
            }
        };
    }

    @Test
    void trechoAchadoSoPelasPalavrasEntraJuntoComOsDoVetor() {
        UUID conteudoId = UUID.randomUUID();
        TrechoProjecao doVetor = trecho(conteudoId, "Servidor", 1, "Backup diário.", 0.3);
        TrechoProjecao dasPalavras = trecho(conteudoId, "Requisitos", 1, "187 funcionários, sede em Campinas.", 0.7);
        when(chunkRepository.buscarParecidos(eq(tenantId), anyString(), eq(5))).thenReturn(List.of(doVetor));
        when(chunkRepository.buscarPorPalavras(eq(tenantId), anyString(), anyString(), eq(5), eq(false)))
                .thenReturn(List.of(dasPalavras));

        List<TrechoEncontrado> encontrados = service.buscar(tenantId, "Quantos funcionários a empresa tem?", 5);

        assertThat(encontrados).extracting(TrechoEncontrado::titulo).containsExactlyInAnyOrder("Servidor", "Requisitos");
    }

    @Test
    void trechoNasDuasBuscasSobeParaOTopo() {
        UUID conteudoId = UUID.randomUUID();
        TrechoProjecao primeiroNoVetor = trecho(conteudoId, "A", 1, "a", 0.2);
        TrechoProjecao segundoNoVetor = trecho(conteudoId, "B", 2, "b", 0.25);
        when(chunkRepository.buscarParecidos(eq(tenantId), anyString(), eq(5)))
                .thenReturn(List.of(primeiroNoVetor, segundoNoVetor));
        when(chunkRepository.buscarPorPalavras(eq(tenantId), anyString(), anyString(), eq(5), eq(false)))
                .thenReturn(List.of(segundoNoVetor));

        assertThat(service.buscar(tenantId, "palavra chave", 5)).extracting(TrechoEncontrado::titulo)
                .containsExactly("B", "A");
    }

    @Test
    void consultaTextualSoTemPalavrasUnidasPorOu() {
        assertThat(BuscaDeTrechosService.consultaTextual("Quantos funcionários? a sede & (x) | ')"))
                .isEqualTo("quantos | funcionários | sede");
        assertThat(BuscaDeTrechosService.consultaTextual("a é ?")).isEmpty();
    }

}
