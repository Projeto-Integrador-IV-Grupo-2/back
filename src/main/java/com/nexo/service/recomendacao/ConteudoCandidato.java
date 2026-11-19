package com.nexo.service.recomendacao;

import com.nexo.entity.Conteudo;
import java.util.UUID;

/**
 * Conteúdo publicado que a IA pode usar como etapa de uma trilha nova.
 *
 * <p>Record desligado do JPA, como {@link TrilhaCandidata}: é lido numa
 * transação curta e chega ao prompt sem lazy loading. A descrição vai curta,
 * para o catálogo caber no prompt.</p>
 */
public record ConteudoCandidato(UUID id, String titulo, String tipo, String categoria, String descricao) {

    static final int TAMANHO_MAXIMO_DESCRICAO = 300;

    public static ConteudoCandidato de(Conteudo conteudo) {
        String descricao = conteudo.getDescricao();
        if (descricao != null && descricao.length() > TAMANHO_MAXIMO_DESCRICAO) {
            descricao = descricao.substring(0, TAMANHO_MAXIMO_DESCRICAO).strip() + "…";
        }
        return new ConteudoCandidato(
                conteudo.getId(),
                conteudo.getTitulo(),
                conteudo.getTipo() == null ? null : conteudo.getTipo().name(),
                conteudo.getCategoria() == null ? null : conteudo.getCategoria().getNome(),
                descricao);
    }
}
