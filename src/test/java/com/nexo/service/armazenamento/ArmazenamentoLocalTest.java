package com.nexo.service.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexo.config.ArmazenamentoProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArmazenamentoLocalTest {

    @TempDir
    Path pasta;

    @Test
    void salvaAbreERemovePelaChave() throws Exception {
        ArmazenamentoLocal armazenamento = new ArmazenamentoLocal(pasta);

        armazenamento.salvar("tenant-1/arquivo-1", new ByteArrayInputStream("conteúdo".getBytes(StandardCharsets.UTF_8)));

        assertThat(pasta.resolve("tenant-1/arquivo-1")).exists();
        try (InputStream lido = armazenamento.abrir("tenant-1/arquivo-1")) {
            assertThat(new String(lido.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("conteúdo");
        }
        armazenamento.remover("tenant-1/arquivo-1");
        assertThat(pasta.resolve("tenant-1/arquivo-1")).doesNotExist();
        // Remover de novo não falha.
        armazenamento.remover("tenant-1/arquivo-1");
    }

    @Test
    void abrirChaveInexistenteLancaNoSuchFile() {
        ArmazenamentoLocal armazenamento = new ArmazenamentoLocal(pasta);

        assertThatThrownBy(() -> armazenamento.abrir("tenant-1/nao-existe")).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void chaveNuncaSaiDaPastaNemUsaNomeDoUsuario() {
        ArmazenamentoLocal armazenamento = new ArmazenamentoLocal(pasta.resolve("raiz"));

        for (String chave : new String[] {"../fora", "a/../../fora", "/absoluto", "nome com espaço.pdf", "", "a//b"}) {
            assertThatThrownBy(() -> armazenamento.salvar(chave, new ByteArrayInputStream(new byte[] {1})))
                    .as(chave)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(Files.exists(pasta.resolve("fora"))).isFalse();
    }

    @Test
    void factoryEntregaOArmazenamentoDaConfiguracao() {
        assertThat(ArmazenamentoFactory.criar(new ArmazenamentoProperties("local", pasta.toString())))
                .isInstanceOf(ArmazenamentoLocal.class);
        assertThatThrownBy(() -> ArmazenamentoFactory.criar(new ArmazenamentoProperties("nuvem", pasta.toString())))
                .isInstanceOf(IllegalStateException.class);
    }
}
