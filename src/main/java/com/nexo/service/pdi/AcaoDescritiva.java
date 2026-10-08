package com.nexo.service.pdi;

import com.nexo.dto.pdi.PdiAcaoRequest;
import com.nexo.entity.PdiAcao;
import com.nexo.entity.PdiItem;
import com.nexo.entity.TipoAcaoPdi;
import com.nexo.security.UsuarioAutenticado;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Ações que não são trilha (mentoria, outro): só descrição e prazo, status manual. */
@Component
public class AcaoDescritiva implements CriadorDeAcao {

    @Override
    public Set<TipoAcaoPdi> tipos() {
        return EnumSet.of(TipoAcaoPdi.MENTORIA, TipoAcaoPdi.OUTRO);
    }

    @Override
    public PdiAcao criar(PdiItem item, PdiAcaoRequest request, UsuarioAutenticado requisitante) {
        return PdiAcao.descritiva(item, request.tipo(), request.prazo(), request.descricao().trim());
    }
}
