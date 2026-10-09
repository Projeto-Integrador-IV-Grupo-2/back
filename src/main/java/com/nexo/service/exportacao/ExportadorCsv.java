package com.nexo.service.exportacao;

import com.nexo.dto.atribuicao.AtribuicaoAcompanhamentoResponse;
import com.nexo.entity.StatusAtribuicao;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Acompanhamento em CSV para o Excel em português: UTF-8 com BOM (sem ele o
 * Excel estraga os acentos), ponto e vírgula como separador e datas dd/MM/aaaa.
 */
@Component
public class ExportadorCsv implements ExportadorAcompanhamento {

    private static final String BOM = "﻿";
    private static final String SEPARADOR = ";";
    private static final String FIM_DE_LINHA = "\r\n";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final List<String> CABECALHO = List.of(
            "Colaborador", "E-mail", "Área", "Trilha", "Status", "Percentual", "Prazo", "Atribuída em",
            "Concluída em");

    private static final Map<StatusAtribuicao, String> ROTULOS = Map.of(
            StatusAtribuicao.NAO_INICIADA, "Não iniciada",
            StatusAtribuicao.EM_ANDAMENTO, "Em andamento",
            StatusAtribuicao.CONCLUIDA, "Concluída",
            StatusAtribuicao.ATRASADA, "Atrasada");

    @Override
    public String formato() {
        return "csv";
    }

    @Override
    public ArquivoExportado exportar(List<AtribuicaoAcompanhamentoResponse> linhas) {
        StringBuilder csv = new StringBuilder(BOM);
        escreverLinha(csv, CABECALHO);
        for (AtribuicaoAcompanhamentoResponse linha : linhas) {
            escreverLinha(csv, List.of(
                    linha.colaborador().nomeCompleto(),
                    linha.colaborador().email(),
                    linha.area() == null ? "" : linha.area().nome(),
                    linha.trilha().nome(),
                    ROTULOS.get(linha.status()),
                    linha.percentual() + "%",
                    formatar(linha.prazo()),
                    formatar(linha.atribuidoEm()),
                    formatar(linha.concluidoEm())));
        }
        return new ArquivoExportado(
                csv.toString().getBytes(StandardCharsets.UTF_8),
                "text/csv; charset=UTF-8",
                "acompanhamento-trilhas.csv");
    }

    private static void escreverLinha(StringBuilder csv, List<String> campos) {
        csv.append(String.join(SEPARADOR, campos.stream().map(ExportadorCsv::escapar).toList()));
        csv.append(FIM_DE_LINHA);
    }

    /** Campo com separador, aspas ou quebra de linha vai entre aspas, com as aspas duplicadas. */
    static String escapar(String campo) {
        if (campo == null) {
            return "";
        }
        if (campo.contains(SEPARADOR) || campo.contains("\"") || campo.contains("\n") || campo.contains("\r")) {
            return "\"" + campo.replace("\"", "\"\"") + "\"";
        }
        return campo;
    }

    private static String formatar(LocalDate data) {
        return data == null ? "" : DATA.format(data);
    }

    private static String formatar(OffsetDateTime instante) {
        return instante == null ? "" : DATA.format(instante);
    }
}
