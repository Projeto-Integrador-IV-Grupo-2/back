package com.nexo.service.exportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexo.dto.atribuicao.AtribuicaoAcompanhamentoResponse;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.exception.ParametroInvalidoException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExportadorCsvTest {

    private final ExportadorCsv exportador = new ExportadorCsv();

    @Test
    void geraUtf8ComBomCabecalhoEUmaLinhaPorAtribuicao() {
        ArquivoExportado arquivo = exportador.exportar(List.of(linha("João Ação", "Segurança", StatusAtribuicao.ATRASADA)));

        String csv = new String(arquivo.conteudo(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿");
        String[] linhas = csv.substring(1).split("\r\n");
        assertThat(linhas).hasSize(2);
        assertThat(linhas[0]).isEqualTo(
                "Colaborador;E-mail;Área;Trilha;Status;Percentual;Prazo;Atribuída em;Concluída em");
        assertThat(linhas[1]).isEqualTo("João Ação;joao@nexo.com;Comercial;Segurança;Atrasada;50%;30/09/2026;;");
        assertThat(arquivo.contentType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(arquivo.nome()).endsWith(".csv");
    }

    @Test
    void campoComSeparadorOuAspasVaiEntreAspas() {
        assertThat(ExportadorCsv.escapar("Vendas; B2B")).isEqualTo("\"Vendas; B2B\"");
        assertThat(ExportadorCsv.escapar("O \"melhor\"")).isEqualTo("\"O \"\"melhor\"\"\"");
        assertThat(ExportadorCsv.escapar(null)).isEmpty();
    }

    @Test
    void fabricaEntregaOCsvEIgnoraMaiusculas() {
        ExportadorFactory fabrica = new ExportadorFactory(List.of(exportador));

        assertThat(fabrica.para("CSV")).isSameAs(exportador);
    }

    @Test
    void fabricaRecusaFormatoDesconhecido() {
        ExportadorFactory fabrica = new ExportadorFactory(List.of(exportador));

        assertThatThrownBy(() -> fabrica.para("pdf")).isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void fabricaAchaExportadorQueDeclaraOFormatoEmMaiusculas() {
        ExportadorAcompanhamento emMaiusculas = org.mockito.Mockito.mock(ExportadorAcompanhamento.class);
        org.mockito.Mockito.when(emMaiusculas.formato()).thenReturn("XLSX");

        assertThat(new ExportadorFactory(List.of(emMaiusculas)).para("xlsx")).isSameAs(emMaiusculas);
    }

    @Test
    void doisExportadoresParaOMesmoFormatoImpedemASubida() {
        assertThatThrownBy(() -> new ExportadorFactory(List.of(exportador, new ExportadorCsv())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("csv");
    }

    private static AtribuicaoAcompanhamentoResponse linha(String colaborador, String trilha, StatusAtribuicao status) {
        return new AtribuicaoAcompanhamentoResponse(
                UUID.randomUUID(),
                new AtribuicaoAcompanhamentoResponse.Colaborador(UUID.randomUUID(), colaborador, "joao@nexo.com"),
                new AtribuicaoAcompanhamentoResponse.Recurso(UUID.randomUUID(), "Comercial"),
                new AtribuicaoAcompanhamentoResponse.Recurso(UUID.randomUUID(), trilha),
                status,
                50,
                LocalDate.of(2026, 9, 30),
                null,
                null);
    }
}
