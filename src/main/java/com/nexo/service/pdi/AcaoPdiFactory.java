package com.nexo.service.pdi;

import com.nexo.dto.pdi.PdiAcaoRequest;
import com.nexo.entity.PdiAcao;
import com.nexo.entity.PdiItem;
import com.nexo.entity.TipoAcaoPdi;
import com.nexo.security.UsuarioAutenticado;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Factory Method das ações do PDI: escolhe pelo tipo o {@link CriadorDeAcao}
 * que cria a ação. Quem pede a ação ({@code PdiAcaoService}) não conhece as
 * classes concretas — um tipo novo é só mais um {@code @Component}.
 */
@Component
public class AcaoPdiFactory {

    private final Map<TipoAcaoPdi, CriadorDeAcao> criadores = new EnumMap<>(TipoAcaoPdi.class);

    public AcaoPdiFactory(List<CriadorDeAcao> criadores) {
        criadores.forEach(criador -> criador.tipos().forEach(tipo -> {
            CriadorDeAcao anterior = this.criadores.putIfAbsent(tipo, criador);
            if (anterior != null) {
                throw new IllegalStateException("Dois criadores de ação para o tipo " + tipo);
            }
        }));
        for (TipoAcaoPdi tipo : TipoAcaoPdi.values()) {
            if (!this.criadores.containsKey(tipo)) {
                throw new IllegalStateException("Nenhum criador de ação para o tipo " + tipo);
            }
        }
    }

    public PdiAcao criar(PdiItem item, PdiAcaoRequest request, UsuarioAutenticado requisitante) {
        return criadores.get(request.tipo()).criar(item, request, requisitante);
    }
}
