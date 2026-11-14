package com.nexo.service.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexo.config.ArmazenamentoProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Factory Method do armazenamento: o tipo configurado escolhe a implementação. */
class ArmazenamentoFactoryTest {

    @TempDir
    Path pasta;

    @Test
    void tipoLocalEntregaOArmazenamentoEmDisco() {
        assertThat(ArmazenamentoFactory.criar(new ArmazenamentoProperties("local", pasta.toString())))
                .isInstanceOf(ArmazenamentoLocal.class);
    }

    @Test
    void semTipoConfiguradoUsaODisco() {
        assertThat(ArmazenamentoFactory.criar(new ArmazenamentoProperties(null, pasta.toString())))
                .isInstanceOf(ArmazenamentoLocal.class);
    }

    @Test
    void ignoraMaiusculasNoTipo() {
        assertThat(ArmazenamentoFactory.criar(new ArmazenamentoProperties("LOCAL", pasta.toString())))
                .isInstanceOf(ArmazenamentoLocal.class);
    }

    @Test
    void tipoDesconhecidoImpedeASubida() {
        assertThatThrownBy(() -> ArmazenamentoFactory.criar(new ArmazenamentoProperties("s3", pasta.toString())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s3");
    }
}
