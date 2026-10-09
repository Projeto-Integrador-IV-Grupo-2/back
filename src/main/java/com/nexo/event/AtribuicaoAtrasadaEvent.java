package com.nexo.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma atribuição passou do prazo sem ser concluída e foi marcada como atrasada
 * pela rotina diária.
 *
 * <p>Publicado por atribuição, não por lote: quem for reagir a isso (cobrar o
 * colaborador, avisar o gestor) precisa saber de qual pessoa se trata.</p>
 *
 * @param atribuicaoId atribuição marcada
 * @param tenantId     tenant dela
 * @param trilhaId     trilha em atraso
 * @param usuarioId    colaborador em atraso
 * @param prazo        prazo que venceu
 */
public record AtribuicaoAtrasadaEvent(UUID atribuicaoId, UUID tenantId, UUID trilhaId, UUID usuarioId, LocalDate prazo) {
}
