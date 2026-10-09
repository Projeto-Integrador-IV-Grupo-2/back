package com.nexo.service.exportacao;

import com.nexo.exception.ParametroInvalidoException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Entrega o exportador de cada formato. Os exportadores são beans do Spring e
 * chegam aqui sozinhos: outro formato (Excel, PDF) só precisa de uma nova
 * implementação de {@link ExportadorAcompanhamento}, sem mudar o controller.
 */
@Component
public class ExportadorFactory {

    private final Map<String, ExportadorAcompanhamento> exportadores = new HashMap<>();

    public ExportadorFactory(List<ExportadorAcompanhamento> disponiveis) {
        disponiveis.forEach(exportador -> exportadores.put(exportador.formato(), exportador));
    }

    public ExportadorAcompanhamento para(String formato) {
        ExportadorAcompanhamento exportador =
                formato == null ? null : exportadores.get(formato.trim().toLowerCase(Locale.ROOT));
        if (exportador == null) {
            throw new ParametroInvalidoException("Formato de exportação não suportado: " + formato);
        }
        return exportador;
    }
}
