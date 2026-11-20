package com.nexo.dto.atribuicao;

import com.nexo.entity.TipoConteudo;
import com.nexo.entity.TrilhaEtapa;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * O andamento de um colaborador numa trilha, etapa por etapa: o que ele já
 * concluiu e quando. É o detalhe de uma linha do acompanhamento.
 *
 * @param atribuicao a mesma linha da listagem (status efetivo, percentual, prazo)
 * @param iniciadoEm quando o colaborador começou a trilha; nulo se não começou
 * @param etapas     etapas da trilha em ordem, com a conclusão deste colaborador
 */
public record AtribuicaoDetalheResponse(
        AtribuicaoAcompanhamentoResponse atribuicao,
        OffsetDateTime iniciadoEm,
        List<EtapaDoColaborador> etapas) {

    /** Uma etapa e se o colaborador a concluiu. Nunca expõe as entities. */
    public record EtapaDoColaborador(
            UUID id,
            int ordem,
            String titulo,
            ConteudoResumo conteudo,
            boolean concluida,
            OffsetDateTime concluidaEm) {

        public static EtapaDoColaborador de(TrilhaEtapa etapa, OffsetDateTime concluidaEm) {
            return new EtapaDoColaborador(
                    etapa.getId(),
                    etapa.getOrdem(),
                    etapa.getTitulo(),
                    new ConteudoResumo(
                            etapa.getConteudo().getId(),
                            etapa.getConteudo().getTitulo(),
                            etapa.getConteudo().getTipo()),
                    concluidaEm != null,
                    concluidaEm);
        }
    }

    public record ConteudoResumo(UUID id, String titulo, TipoConteudo tipo) {
    }
}
