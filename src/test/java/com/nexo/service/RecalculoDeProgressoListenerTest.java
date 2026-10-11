package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.event.EtapaConcluidaEvent;
import com.nexo.event.EtapasDaTrilhaAlteradasEvent;
import com.nexo.repository.EtapaProgressoRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaEtapaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecalculoDeProgressoListenerTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;

    @Mock
    private TrilhaEtapaRepository trilhaEtapaRepository;

    @Mock
    private EtapaProgressoRepository etapaProgressoRepository;

    private RecalculoDeProgressoListener listener;
    private Trilha trilha;
    private Usuario usuario;

    @BeforeEach
    void prepara() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        listener = new RecalculoDeProgressoListener(trilhaAtribuicaoRepository, trilhaEtapaRepository,
                etapaProgressoRepository, new CalculadoraDePercentual(), clock);
        trilha = new Trilha();
        ReflectionTestUtils.setField(trilha, "id", UUID.randomUUID());
        usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
    }

    @Test
    void concluirEtapaAtualizaOPercentual() {
        TrilhaAtribuicao atribuicao = atribuicao(StatusAtribuicao.EM_ANDAMENTO);
        quandoConcluiu(atribuicao, 1, 4);

        listener.aoConcluirEtapa(eventoDeConclusao());

        assertThat(atribuicao.getPercentual()).isEqualTo(25);
        assertThat(atribuicao.getStatus()).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
        assertThat(atribuicao.getConcluidoEm()).isNull();
    }

    @Test
    void concluirAUltimaEtapaConcluiATrilha() {
        TrilhaAtribuicao atribuicao = atribuicao(StatusAtribuicao.ATRASADA);
        quandoConcluiu(atribuicao, 3, 3);

        listener.aoConcluirEtapa(eventoDeConclusao());

        assertThat(atribuicao.getPercentual()).isEqualTo(100);
        assertThat(atribuicao.getStatus()).isEqualTo(StatusAtribuicao.CONCLUIDA);
        assertThat(atribuicao.getConcluidoEm()).isNotNull();
    }

    @Test
    void primeiraEtapaDeAtribuicaoNaoIniciadaColocaEmAndamento() {
        TrilhaAtribuicao atribuicao = atribuicao(StatusAtribuicao.NAO_INICIADA);
        quandoConcluiu(atribuicao, 1, 2);

        listener.aoConcluirEtapa(eventoDeConclusao());

        assertThat(atribuicao.getStatus()).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
    }

    @Test
    void novaEtapaReduzOPercentualDeQuemEstaEmAndamento() {
        TrilhaAtribuicao atribuicao = atribuicao(StatusAtribuicao.EM_ANDAMENTO);
        atribuicao.setPercentual(50);
        when(trilhaEtapaRepository.countByTrilhaId(trilha.getId())).thenReturn(3L);
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndStatusNot(trilha.getId(), StatusAtribuicao.CONCLUIDA))
                .thenReturn(List.of(atribuicao));
        when(etapaProgressoRepository.contarConcluidas(trilha.getId(), usuario.getId())).thenReturn(1L);

        listener.aoAlterarEtapas(new EtapasDaTrilhaAlteradasEvent(trilha.getId(), TENANT));

        assertThat(atribuicao.getPercentual()).isEqualTo(33);
    }

    @Test
    void trilhaConcluidaPermaneceConcluida() {
        TrilhaAtribuicao atribuicao = atribuicao(StatusAtribuicao.CONCLUIDA);
        atribuicao.setPercentual(100);
        when(trilhaEtapaRepository.countByTrilhaId(trilha.getId())).thenReturn(5L);
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilha.getId(), usuario.getId()))
                .thenReturn(Optional.of(atribuicao));

        listener.aoConcluirEtapa(eventoDeConclusao());

        assertThat(atribuicao.getStatus()).isEqualTo(StatusAtribuicao.CONCLUIDA);
        assertThat(atribuicao.getPercentual()).isEqualTo(100);
        verify(trilhaAtribuicaoRepository, never()).save(any());
    }

    private void quandoConcluiu(TrilhaAtribuicao atribuicao, long concluidas, long total) {
        when(trilhaEtapaRepository.countByTrilhaId(trilha.getId())).thenReturn(total);
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilha.getId(), usuario.getId()))
                .thenReturn(Optional.of(atribuicao));
        when(etapaProgressoRepository.contarConcluidas(trilha.getId(), usuario.getId())).thenReturn(concluidas);
    }

    private EtapaConcluidaEvent eventoDeConclusao() {
        return new EtapaConcluidaEvent(UUID.randomUUID(), trilha.getId(), TENANT, usuario.getId());
    }

    private TrilhaAtribuicao atribuicao(StatusAtribuicao status) {
        return TrilhaAtribuicao.nova(trilha, usuario, usuario, null, status);
    }
}
