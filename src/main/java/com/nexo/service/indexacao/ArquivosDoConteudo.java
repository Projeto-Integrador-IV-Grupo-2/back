package com.nexo.service.indexacao;

import com.nexo.entity.Arquivo;
import com.nexo.entity.Conteudo;
import com.nexo.repository.ArquivoRepository;
import com.nexo.service.armazenamento.ArmazenamentoDeArquivos;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Texto dos arquivos anexados ao conteúdo (PDF, DOCX, TXT, MD), na ordem de
 * envio — a fonte que o upload de documentos (issue #55) acrescenta à
 * indexação, depois dos campos do próprio conteúdo.
 *
 * <p>Arquivo ilegível ou ausente do armazenamento lança exceção: o
 * {@link IndexacaoService} registra a falha do documento e tenta de novo
 * depois, sem afetar a publicação.</p>
 */
@Component
@Order(1)
public class ArquivosDoConteudo implements FonteDeTextoDoConteudo {

    private final ArquivoRepository arquivoRepository;
    private final ArmazenamentoDeArquivos armazenamento;
    private final ExtratorDeTextoFactory extratores;

    public ArquivosDoConteudo(ArquivoRepository arquivoRepository,
                              ArmazenamentoDeArquivos armazenamento,
                              ExtratorDeTextoFactory extratores) {
        this.arquivoRepository = arquivoRepository;
        this.armazenamento = armazenamento;
        this.extratores = extratores;
    }

    @Override
    public String textoDe(Conteudo conteudo) {
        List<String> textos = new ArrayList<>();
        for (Arquivo arquivo : arquivoRepository.findByTenantIdAndConteudoIdOrderByCriadoEmAsc(
                conteudo.getTenant().getId(), conteudo.getId())) {
            String texto = extrair(arquivo);
            if (texto != null && !texto.isBlank()) {
                textos.add(texto.strip());
            }
        }
        return String.join("\n\n", textos);
    }

    private String extrair(Arquivo arquivo) {
        try (InputStream conteudo = armazenamento.abrir(arquivo.getChaveArmazenamento())) {
            return extratores.para(arquivo.getMimeType()).extrair(conteudo);
        } catch (IOException ex) {
            throw new UncheckedIOException("Não foi possível ler o arquivo " + arquivo.getId(), ex);
        }
    }
}
