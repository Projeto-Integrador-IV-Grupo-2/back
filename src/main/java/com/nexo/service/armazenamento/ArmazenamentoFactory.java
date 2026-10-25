package com.nexo.service.armazenamento;

import com.nexo.config.ArmazenamentoProperties;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Factory Method do armazenamento: escolhe a implementação por
 * {@code app.armazenamento.tipo}. Guardar na nuvem é escrever outra
 * implementação e acrescentar um caso aqui — quem usa
 * {@link ArmazenamentoDeArquivos} não muda.
 */
public final class ArmazenamentoFactory {

    private ArmazenamentoFactory() {
    }

    public static ArmazenamentoDeArquivos criar(ArmazenamentoProperties properties) {
        String tipo = properties.tipo().toLowerCase(Locale.ROOT);
        return switch (tipo) {
            case "local" -> new ArmazenamentoLocal(Path.of(properties.diretorio()));
            default -> throw new IllegalStateException(
                    "app.armazenamento.tipo desconhecido: '" + properties.tipo() + "' (use local)");
        };
    }
}
