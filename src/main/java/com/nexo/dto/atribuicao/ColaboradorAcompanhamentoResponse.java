package com.nexo.dto.atribuicao;

import com.nexo.entity.Usuario;
import java.util.List;

/**
 * Visão de um colaborador no acompanhamento: todas as trilhas dele e os totais
 * por status.
 */
public record ColaboradorAcompanhamentoResponse(
        AtribuicaoAcompanhamentoResponse.Colaborador colaborador,
        AtribuicaoAcompanhamentoResponse.Recurso area,
        TotaisPorStatus totais,
        List<AtribuicaoAcompanhamentoResponse> trilhas) {

    public static ColaboradorAcompanhamentoResponse de(Usuario usuario, List<AtribuicaoAcompanhamentoResponse> trilhas) {
        return new ColaboradorAcompanhamentoResponse(
                new AtribuicaoAcompanhamentoResponse.Colaborador(
                        usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail()),
                usuario.getArea() == null
                        ? null
                        : new AtribuicaoAcompanhamentoResponse.Recurso(
                                usuario.getArea().getId(), usuario.getArea().getNome()),
                TotaisPorStatus.de(trilhas),
                trilhas);
    }
}
