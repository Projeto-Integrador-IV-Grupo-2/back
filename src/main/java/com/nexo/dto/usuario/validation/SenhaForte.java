package com.nexo.dto.usuario.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Política mínima de senha: 8+ caracteres, com ao menos uma letra maiúscula,
 * uma minúscula e um número.
 *
 * <p>Aplicada apenas no cadastro ({@code CriarUsuarioRequest.senha}) — troca
 * de senha de um usuário existente é um fluxo à parte, fora do escopo deste
 * card, e pode reutilizar esta mesma anotação quando for implementado.</p>
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SenhaForteValidator.class)
public @interface SenhaForte {

    String message() default "senha deve ter ao menos 8 caracteres, incluindo letra maiúscula, minúscula e número";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
