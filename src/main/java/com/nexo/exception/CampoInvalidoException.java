package com.nexo.exception;

/**
 * Valor de um campo do corpo recusado por uma regra que a Bean Validation não
 * cobre (ex.: link de vídeo que o Vimeo não reconhece). Mapeada para HTTP 400
 * pelo {@link GlobalExceptionHandler}, com o erro no {@code fieldErrors} — o
 * front exibe a mensagem junto do campo.
 */
public class CampoInvalidoException extends RuntimeException {

    private final String campo;

    public CampoInvalidoException(String campo, String message) {
        super(message);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
