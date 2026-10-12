package com.nexo.dto.atribuicao;

import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.entity.Area;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma linha do acompanhamento: o andamento de um colaborador numa trilha.
 * Nunca expõe as entities.
 *
 * @param area   área do colaborador (pode ser nula)
 * @param status status efetivo: prazo vencido e não concluída aparece como ATRASADA
 */
public record AtribuicaoAcompanhamentoResponse(
        UUID id,
        Colaborador colaborador,
        Recurso area,
        Recurso trilha,
        StatusAtribuicao status,
        int percentual,
        LocalDate prazo,
        OffsetDateTime atribuidoEm,
        OffsetDateTime concluidoEm) {

    public record Colaborador(UUID id, String nomeCompleto, String email) {
    }

    public record Recurso(UUID id, String nome) {
    }

    public static AtribuicaoAcompanhamentoResponse de(TrilhaAtribuicao atribuicao, StatusAtribuicao statusEfetivo) {
        Usuario usuario = atribuicao.getUsuario();
        return new AtribuicaoAcompanhamentoResponse(
                atribuicao.getId(),
                new Colaborador(usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail()),
                recursoDe(usuario.getArea()),
                recursoDe(atribuicao.getTrilha()),
                statusEfetivo,
                ProgressoTrilhaResponse.percentualDe(statusEfetivo),
                atribuicao.getPrazo(),
                atribuicao.getAtribuidoEm(),
                atribuicao.getConcluidoEm());
    }

    private static Recurso recursoDe(Area area) {
        return area == null ? null : new Recurso(area.getId(), area.getNome());
    }

    private static Recurso recursoDe(Trilha trilha) {
        return new Recurso(trilha.getId(), trilha.getNome());
    }
}
