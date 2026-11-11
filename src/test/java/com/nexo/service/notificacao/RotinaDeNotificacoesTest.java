package com.nexo.service.notificacao;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotinaDeNotificacoesTest {

    @Mock
    private GeradorDeNotificacoes gerador;

    @InjectMocks
    private RotinaDeNotificacoes rotina;

    @Test
    void tentaLimparMesmoQuandoGeracaoDeAvisosFalha() {
        when(gerador.avisarPrazosProximos()).thenThrow(new IllegalStateException("falha ao gerar avisos"));

        assertThatCode(rotina::rodar).doesNotThrowAnyException();

        verify(gerador).apagarLidasAntigas();
    }

    @Test
    void registraFalhaNaLimpezaSemInterromperARotina() {
        when(gerador.apagarLidasAntigas()).thenThrow(new IllegalStateException("falha ao limpar"));

        assertThatCode(rotina::rodar).doesNotThrowAnyException();

        verify(gerador).avisarPrazosProximos();
    }
}
