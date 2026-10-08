package com.nexo.dto.pdi.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Coerência entre o tipo da ação e seus campos: {@code TRILHA} exige
 * {@code trilhaId}; os outros tipos exigem {@code descricao} e não aceitam
 * {@code trilhaId}. O erro é reportado no campo que falta (ou sobra), para o
 * front mostrar a mensagem ao lado dele.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AcaoCoerenteComTipoValidator.class)
public @interface AcaoCoerenteComTipo {

    String message() default "campos incoerentes com o tipo da ação";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
