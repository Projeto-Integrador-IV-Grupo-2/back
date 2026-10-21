package com.nexo.service.indexacao;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Factory Method dos extratores: entrega o {@link ExtratorDeTexto} conforme o
 * tipo MIME do arquivo (PDF, DOCX, TXT ou MD).
 *
 * <p>Os extratores chegam pela injeção do Spring — um formato novo é só um
 * novo {@code @Component}, sem mexer aqui.</p>
 */
@Component
public class ExtratorDeTextoFactory {

    private final Map<String, ExtratorDeTexto> porTipo = new HashMap<>();

    public ExtratorDeTextoFactory(List<ExtratorDeTexto> extratores) {
        extratores.forEach(extrator -> extrator.tiposSuportados().forEach(tipo -> {
            ExtratorDeTexto anterior = porTipo.putIfAbsent(tipo, extrator);
            if (anterior != null) {
                throw new IllegalStateException("Dois extratores para o tipo " + tipo);
            }
        }));
    }

    /**
     * @param tipoMime tipo do arquivo; parâmetros como {@code ;charset=utf-8} são ignorados
     * @throws FormatoNaoSuportadoException quando nenhum extrator atende o tipo
     */
    public ExtratorDeTexto para(String tipoMime) {
        String tipo = normalizar(tipoMime);
        ExtratorDeTexto extrator = porTipo.get(tipo);
        if (extrator == null) {
            throw new FormatoNaoSuportadoException(tipoMime);
        }
        return extrator;
    }

    private static String normalizar(String tipoMime) {
        if (tipoMime == null) {
            return "";
        }
        int parametros = tipoMime.indexOf(';');
        String tipo = parametros >= 0 ? tipoMime.substring(0, parametros) : tipoMime;
        return tipo.trim().toLowerCase(Locale.ROOT);
    }
}
