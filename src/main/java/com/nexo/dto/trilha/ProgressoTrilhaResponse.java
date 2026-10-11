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
 * <p>{@code percentual} é etapas concluídas / total de etapas (issue #40),
 * gravado na atribuição e recalculado a cada etapa concluída ou alteração das
 * etapas da trilha. Trilha concluída aparece sempre com 100.</p>
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
                percentualDe(progresso),
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

    /**
     * Mesmo progresso com outro status — usado para expor o status efetivo, em
     * que prazo vencido conta como atrasada mesmo antes de a situação ser
     * gravada no banco. O percentual não muda: atrasar não desfaz etapas.
     */
    public ProgressoTrilhaResponse comStatus(StatusAtribuicao novoStatus) {
        if (novoStatus == status) {
            return this;
        }
        return new ProgressoTrilhaResponse(
                id, trilha, novoStatus, percentual, prazo, atribuidoEm, concluidoEm);
    }

    private static Recurso recursoDe(Trilha trilha) {
        return new Recurso(trilha.getId(), trilha.getNome());
    }

    /**
     * Percentual exibido de uma atribuição. Concluída é sempre 100, mesmo que
     * tenha sido concluída manualmente numa trilha sem etapas.
     */
    public static int percentualDe(TrilhaAtribuicao atribuicao) {
        return atribuicao.getStatus() == StatusAtribuicao.CONCLUIDA ? 100 : atribuicao.getPercentual();
    }
}
