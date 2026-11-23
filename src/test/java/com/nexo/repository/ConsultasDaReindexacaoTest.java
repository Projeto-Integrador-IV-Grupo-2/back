package com.nexo.repository;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * As consultas da {@code RotinaDeReindexacao} rodam no PostgreSQL real: um
 * literal de enum no JPQL virava cast para um tipo inexistente
 * ({@code 'FALHOU'::StatusIndexacao}) e a rotina quebrava a cada minuto.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConsultasDaReindexacaoTest {

    @Autowired
    private IndexacaoConteudoRepository indexacaoRepository;

    @Autowired
    private ConteudoRepository conteudoRepository;

    @Test
    void falhasAbandonadasConsultaSemErroNoBanco() {
        assertThatCode(() -> indexacaoRepository.idsDeFalhasAbandonadasDesde(OffsetDateTime.now(),
                PageRequest.of(0, 25))).doesNotThrowAnyException();
    }

    @Test
    void publicadosSemIndexacaoConsultaSemErroNoBanco() {
        assertThatCode(() -> conteudoRepository.idsPublicadosSemIndexacao(OffsetDateTime.now(),
                PageRequest.of(0, 25))).doesNotThrowAnyException();
    }

    @Autowired
    private ConteudoChunkRepository chunkRepository;

    @Test
    void buscaPorPalavrasRodaNoBanco() {
        String vetor = "[" + "0.1,".repeat(767) + "0.1]";
        assertThatCode(() -> chunkRepository.buscarPorPalavras(java.util.UUID.randomUUID(),
                "funcionários | sede | como", vetor, 5, false)).doesNotThrowAnyException();
    }

}
