package com.nexo.service.armazenamento;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Arquivos numa pasta do servidor ({@code app.armazenamento.diretorio}).
 *
 * <p>A chave vira o caminho relativo dentro da pasta. Ela é gerada pela
 * aplicação, mas mesmo assim é conferida: só letras, números, hífen e barra,
 * e o caminho final precisa ficar dentro da pasta — nada de {@code ..}.</p>
 */
public class ArmazenamentoLocal implements ArmazenamentoDeArquivos {

    private static final Pattern CHAVE_VALIDA = Pattern.compile("[A-Za-z0-9-]+(/[A-Za-z0-9-]+)*");

    private final Path raiz;

    public ArmazenamentoLocal(Path raiz) {
        this.raiz = raiz.toAbsolutePath().normalize();
    }

    @Override
    public void salvar(String chave, InputStream conteudo) throws IOException {
        Path destino = caminho(chave);
        Files.createDirectories(destino.getParent());
        Files.copy(conteudo, destino, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream abrir(String chave) throws IOException {
        return Files.newInputStream(caminho(chave));
    }

    @Override
    public void remover(String chave) throws IOException {
        Files.deleteIfExists(caminho(chave));
    }

    private Path caminho(String chave) {
        if (chave == null || !CHAVE_VALIDA.matcher(chave).matches()) {
            throw new IllegalArgumentException("Chave de armazenamento inválida");
        }
        Path caminho = raiz.resolve(chave).normalize();
        if (!caminho.startsWith(raiz)) {
            throw new IllegalArgumentException("Chave de armazenamento fora da pasta");
        }
        return caminho;
    }
}
