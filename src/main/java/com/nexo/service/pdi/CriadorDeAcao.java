package com.nexo.service.pdi;

import com.nexo.dto.pdi.PdiAcaoRequest;
import com.nexo.entity.PdiAcao;
import com.nexo.entity.PdiItem;
import com.nexo.entity.TipoAcaoPdi;
import com.nexo.security.UsuarioAutenticado;
import java.util.Set;

/**
 * Produto concreto do Factory Method de ações do PDI: cada implementação sabe
 * criar as ações de alguns tipos. Escolhida por {@link AcaoPdiFactory}.
 */
public interface CriadorDeAcao {

    /** Tipos de ação que este criador atende. */
    Set<TipoAcaoPdi> tipos();

    /** Cria (sem gravar) a ação do item, com os efeitos colaterais do tipo. */
    PdiAcao criar(PdiItem item, PdiAcaoRequest request, UsuarioAutenticado requisitante);
}
