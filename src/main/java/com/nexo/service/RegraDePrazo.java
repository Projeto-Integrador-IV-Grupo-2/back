package com.nexo.service;

import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.TrilhaAtribuicao;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Único lugar que responde "esta atribuição está atrasada?".
 *
 * <p>É um bean sem estado (singleton do Spring) usado pelos dois caminhos que
 * precisam da resposta: a rotina diária, que persiste o status, e a leitura,
 * que precisa mostrar o atraso <em>sem</em> esperar a rotina do dia seguinte.
 * Ter a regra em um lugar só é o que garante que os dois nunca discordem.</p>
 *
 * <p>O {@link Clock} injetado é o que torna a regra testável: nenhuma chamada
 * a {@code LocalDate.now()} sem relógio.</p>
 */
@Component
public class RegraDePrazo {

    private final Clock clock;

    public RegraDePrazo(Clock clock) {
        this.clock = clock;
    }

    /**
     * Atrasada é a atribuição com prazo vencido que ainda não foi concluída.
     * Trilha concluída nunca fica atrasada, mesmo que tenha passado do prazo —
     * o prazo já não significa nada depois da conclusão.
     */
    public boolean estaAtrasada(TrilhaAtribuicao atribuicao) {
        return atribuicao.getPrazo() != null
                && atribuicao.getStatus() != StatusAtribuicao.CONCLUIDA
                && atribuicao.getPrazo().isBefore(hoje());
    }

    /**
     * Status que o cliente deve ver: o gravado, ou {@code ATRASADA} quando o
     * prazo venceu e a rotina ainda não rodou.
     */
    public StatusAtribuicao statusEfetivo(TrilhaAtribuicao atribuicao) {
        return estaAtrasada(atribuicao) ? StatusAtribuicao.ATRASADA : atribuicao.getStatus();
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }
}
