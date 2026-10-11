package com.nexo.service;

import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.event.EtapaConcluidaEvent;
import com.nexo.event.EtapasDaTrilhaAlteradasEvent;
import com.nexo.repository.EtapaProgressoRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaEtapaRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer do progresso por etapa (issue #40): mantém o {@code percentual} da
 * atribuição em dia e conclui a trilha quando a última etapa é concluída.
 *
 * <p>Os listeners são síncronos e rodam dentro da transação de quem publicou o
 * evento — a resposta do endpoint já sai com o percentual recalculado.</p>
 *
 * <p>Trilha já concluída permanece concluída: nem uma etapa nova nem a remoção
 * de uma etapa reabrem quem já terminou.</p>
 */
@Component
public class RecalculoDeProgressoListener {

    private final TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;
    private final TrilhaEtapaRepository trilhaEtapaRepository;
    private final EtapaProgressoRepository etapaProgressoRepository;
    private final CalculadoraDePercentual calculadora;
    private final Clock clock;

    public RecalculoDeProgressoListener(TrilhaAtribuicaoRepository trilhaAtribuicaoRepository,
                                        TrilhaEtapaRepository trilhaEtapaRepository,
                                        EtapaProgressoRepository etapaProgressoRepository,
                                        CalculadoraDePercentual calculadora,
                                        Clock clock) {
        this.trilhaAtribuicaoRepository = trilhaAtribuicaoRepository;
        this.trilhaEtapaRepository = trilhaEtapaRepository;
        this.etapaProgressoRepository = etapaProgressoRepository;
        this.calculadora = calculadora;
        this.clock = clock;
    }

    @EventListener
    public void aoConcluirEtapa(EtapaConcluidaEvent evento) {
        long total = trilhaEtapaRepository.countByTrilhaId(evento.trilhaId());
        trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(evento.trilhaId(), evento.usuarioId())
                .ifPresent(atribuicao -> recalcular(atribuicao, total));
    }

    @EventListener
    public void aoAlterarEtapas(EtapasDaTrilhaAlteradasEvent evento) {
        long total = trilhaEtapaRepository.countByTrilhaId(evento.trilhaId());
        trilhaAtribuicaoRepository
                .findByTrilhaIdAndStatusNot(evento.trilhaId(), StatusAtribuicao.CONCLUIDA)
                .forEach(atribuicao -> recalcular(atribuicao, total));
    }

    private void recalcular(TrilhaAtribuicao atribuicao, long total) {
        if (atribuicao.getStatus() == StatusAtribuicao.CONCLUIDA) {
            return;
        }
        long concluidas = etapaProgressoRepository.contarConcluidas(
                atribuicao.getTrilha().getId(), atribuicao.getUsuario().getId());
        atribuicao.setPercentual(calculadora.percentual(concluidas, total));

        if (calculadora.completa(concluidas, total)) {
            atribuicao.setStatus(StatusAtribuicao.CONCLUIDA);
            atribuicao.setConcluidoEm(OffsetDateTime.now(clock));
        } else if (atribuicao.getStatus() == StatusAtribuicao.NAO_INICIADA && concluidas > 0) {
            atribuicao.setStatus(StatusAtribuicao.EM_ANDAMENTO);
        }
        trilhaAtribuicaoRepository.save(atribuicao);
    }
}
