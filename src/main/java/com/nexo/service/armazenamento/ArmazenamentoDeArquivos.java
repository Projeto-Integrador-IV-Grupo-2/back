package com.nexo.service.armazenamento;

import java.io.IOException;
import java.io.InputStream;

/**
 * Onde os binários dos arquivos ficam guardados, fora do banco.
 *
 * <p>Quem usa só conhece a chave gerada pela aplicação — nunca o nome que o
 * usuário enviou. Hoje há o disco local; um armazenamento em nuvem é outra
 * implementação escolhida pela {@link ArmazenamentoFactory}, sem mudar quem
 * chama.</p>
 */
public interface ArmazenamentoDeArquivos {

    /** Grava o conteúdo sob a chave. Não fecha o stream. */
    void salvar(String chave, InputStream conteudo) throws IOException;

    /** Abre o arquivo da chave para leitura; quem chama fecha o stream. */
    InputStream abrir(String chave) throws IOException;

    /** Remove o arquivo da chave; não faz nada se ele não existir. */
    void remover(String chave) throws IOException;
}
