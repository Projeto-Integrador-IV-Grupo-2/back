package com.nexo.service.exportacao;

import com.nexo.dto.atribuicao.AtribuicaoAcompanhamentoResponse;
import java.util.List;

/**
 * Gera o arquivo do acompanhamento num formato. Cada formato é um bean; a
 * {@link ExportadorFactory} escolhe qual usar.
 */
public interface ExportadorAcompanhamento {

    /** Identificador usado em {@code ?formato=} (ex.: "csv"). */
    String formato();

    ArquivoExportado exportar(List<AtribuicaoAcompanhamentoResponse> linhas);
}
