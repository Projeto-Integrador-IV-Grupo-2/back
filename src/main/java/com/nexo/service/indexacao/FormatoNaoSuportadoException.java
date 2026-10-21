package com.nexo.service.indexacao;

/**
 * Não existe extrator para o tipo do arquivo. Na indexação vira falha
 * registrada em {@code indexacao_conteudo}, nunca erro para o usuário.
 */
public class FormatoNaoSuportadoException extends RuntimeException {

    public FormatoNaoSuportadoException(String tipo) {
        super("Formato de arquivo não suportado para indexação: " + tipo);
    }
}
