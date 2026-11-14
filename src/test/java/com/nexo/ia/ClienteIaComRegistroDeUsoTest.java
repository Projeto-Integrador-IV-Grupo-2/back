package com.nexo.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.config.IaProperties;
import com.nexo.event.ChamadaIaRealizadaEvent;
import com.nexo.event.ChamadaIaRealizadaEvent.Operacao;
import com.nexo.exception.IaIndisponivelException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Observer do uso da IA: cada chamada vira um evento, sem mudar o resultado. */
@ExtendWith(MockitoExtension.class)
class ClienteIaComRegistroDeUsoTest {

    @Mock
    private ClienteIa real;

    @Mock
    private ApplicationEventPublisher publisher;

    private ClienteIaComRegistroDeUso cliente;

    @BeforeEach
    void setUp() {
        IaProperties properties = new IaProperties("gemini", "chave", null, "modelo-chat", "modelo-vetor", null,
                null, null, null, null, null, null);
        cliente = new ClienteIaComRegistroDeUso(real, publisher, properties);
    }

    @Test
    void geracaoPublicaModeloTokensEDevolveARespostaIntacta() {
        RespostaIa resposta = new RespostaIa("oi", 12, 5);
        when(real.gerarResposta("sistema", List.of())).thenReturn(resposta);

        assertThat(cliente.gerarResposta("sistema", List.of())).isSameAs(resposta);

        ChamadaIaRealizadaEvent evento = eventoPublicado();
        assertThat(evento.provedor()).isEqualTo("gemini");
        assertThat(evento.modelo()).isEqualTo("modelo-chat");
        assertThat(evento.operacao()).isEqualTo(Operacao.GERACAO);
        assertThat(evento.tokensEntrada()).isEqualTo(12);
        assertThat(evento.tokensSaida()).isEqualTo(5);
        assertThat(evento.sucesso()).isTrue();
        assertThat(evento.duracao().isNegative()).isFalse();
    }

    @Test
    void embeddingPublicaQuantosTextosForam() {
        when(real.gerarEmbeddings(List.of("a", "b"), FinalidadeEmbedding.DOCUMENTO))
                .thenReturn(List.of(new float[]{1}, new float[]{2}));

        cliente.gerarEmbeddings(List.of("a", "b"), FinalidadeEmbedding.DOCUMENTO);

        ChamadaIaRealizadaEvent evento = eventoPublicado();
        assertThat(evento.modelo()).isEqualTo("modelo-vetor");
        assertThat(evento.operacao()).isEqualTo(Operacao.EMBEDDING);
        assertThat(evento.itens()).isEqualTo(2);
    }

    @Test
    void falhaDoProvedorPublicaSemSucessoERepassaOErro() {
        IaIndisponivelException erro = new IaIndisponivelException("fora do ar");
        when(real.gerarResposta(any(), any())).thenThrow(erro);

        assertThatThrownBy(() -> cliente.gerarResposta("sistema", List.of())).isSameAs(erro);

        assertThat(eventoPublicado().sucesso()).isFalse();
    }

    @Test
    void falhaAoPublicarNaoDerrubaAChamada() {
        RespostaIa resposta = new RespostaIa("oi", 1, 1);
        when(real.gerarResposta(any(), any())).thenReturn(resposta);
        doThrow(new IllegalStateException("listener quebrou")).when(publisher).publishEvent(any(Object.class));

        assertThat(cliente.gerarResposta("sistema", List.of())).isSameAs(resposta);
    }

    private ChamadaIaRealizadaEvent eventoPublicado() {
        ArgumentCaptor<ChamadaIaRealizadaEvent> evento = ArgumentCaptor.forClass(ChamadaIaRealizadaEvent.class);
        verify(publisher).publishEvent(evento.capture());
        return evento.getValue();
    }
}
