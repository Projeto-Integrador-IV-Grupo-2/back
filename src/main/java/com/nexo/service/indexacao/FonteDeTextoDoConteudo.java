package com.nexo.service.indexacao;

import com.nexo.entity.Conteudo;

/**
 * De onde sai o texto indexado de um conteúdo.
 *
 * <p>Ponto de extensão da indexação: hoje só os campos do próprio conteúdo
 * ({@link CamposDoConteudo}); o upload de documentos (issue #55) acrescenta
 * uma fonte que lê os arquivos anexados e usa a {@link ExtratorDeTextoFactory}.
 * O {@link IndexacaoService} junta o texto de todas as fontes.</p>
 *
 * <p>Roda dentro de uma transação de leitura: pode navegar pelas associações
 * do conteúdo.</p>
 */
public interface FonteDeTextoDoConteudo {

    /** Texto do conteúdo nesta fonte; vazio quando não houver nada. */
    String textoDe(Conteudo conteudo);
}
