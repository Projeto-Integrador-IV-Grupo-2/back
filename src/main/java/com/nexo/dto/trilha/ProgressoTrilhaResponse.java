package com.nexo.dto.trilha;

import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Progresso do usuário autenticado numa trilha (Card 07).
 *
 * <p>{@code percentual} é uma aproximação a partir do {@code status} — o
 * cálculo "de verdade" (vídeos/etapas assistidos) depende de
 * {@code etapa_progresso}, que depende de {@code trilha_etapa}/{@code conteudo}
 * (outro card, ainda não implementados). Nada disso é persistido: é derivado
 * aqui, na resposta, então trocar a fórmula não exige migration.</p>
 */
public record ProgressoTrilhaResponse(
        UUID id,
        Recurso trilha,
        StatusAtribuicao status,
        int percentual,
        LocalDate prazo,
        OffsetDateTime atribuidoEm,
        OffsetDateTime concluidoEm) {

    /** Resumo da trilha — nunca a entidade completa. */
    public record Recurso(UUID id, String nome) {
    }

    public static ProgressoTrilhaResponse de(TrilhaAtribuicao progresso) {
        return new ProgressoTrilhaResponse(
                progresso.getId(),
                recursoDe(progresso.getTrilha()),
                progresso.getStatus(),
                percentualDe(progresso.getStatus()),
                progresso.getPrazo(),
                progresso.getAtribuidoEm(),
                progresso.getConcluidoEm());
    }

    /**
     * Representa a ausência de registro em {@code trilha_atribuicao} (usuário
     * nunca acessou a trilha) — usado pelo detalhe de trilha (Card 08), que
     * precisa mostrar "não iniciado" em vez de 404. {@code id} nulo é o sinal
     * de que não existe atribuição persistida.
     */
    public static ProgressoTrilhaResponse naoIniciado(Trilha trilha) {
        return new ProgressoTrilhaResponse(
                null, recursoDe(trilha), StatusAtribuicao.NAO_INICIADA, 0, null, null, null);
    }

    private static Recurso recursoDe(Trilha trilha) {
        return new Recurso(trilha.getId(), trilha.getNome());
    }

    private static int percentualDe(StatusAtribuicao status) {
        return switch (status) {
            case NAO_INICIADA -> 0;
            case EM_ANDAMENTO, ATRASADA -> 50;
            case CONCLUIDA -> 100;
        };
    }
}
