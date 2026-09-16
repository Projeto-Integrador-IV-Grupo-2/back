package com.nexo.dto.usuario.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Verifica apenas o formato da senha (complexidade). Ausência/valor em
 * branco é responsabilidade do {@code @NotBlank}, que deve ser combinado com
 * esta anotação — por isso {@code null}/vazio é tratado aqui como válido,
 * evitando mensagens de erro duplicadas para o mesmo campo.
 */
public class SenhaForteValidator implements ConstraintValidator<SenhaForte, String> {

    private static final Pattern POLITICA_MINIMA =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext context) {
        if (senha == null || senha.isBlank()) {
            return true;
        }
        return POLITICA_MINIMA.matcher(senha).matches();
    }
}
