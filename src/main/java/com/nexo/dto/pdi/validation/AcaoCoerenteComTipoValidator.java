package com.nexo.dto.pdi.validation;

import com.nexo.dto.pdi.PdiAcaoRequest;
import com.nexo.entity.TipoAcaoPdi;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Tipo ausente é responsabilidade do {@code @NotNull} de {@code tipo} — por
 * isso é tratado aqui como válido, evitando mensagem duplicada.
 */
public class AcaoCoerenteComTipoValidator implements ConstraintValidator<AcaoCoerenteComTipo, PdiAcaoRequest> {

    @Override
    public boolean isValid(PdiAcaoRequest acao, ConstraintValidatorContext context) {
        if (acao == null || acao.tipo() == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        if (acao.tipo() == TipoAcaoPdi.TRILHA) {
            return acao.trilhaId() != null || erro(context, "trilhaId", "trilha é obrigatória para ação do tipo TRILHA");
        }
        if (acao.trilhaId() != null) {
            return erro(context, "trilhaId", "só ação do tipo TRILHA aponta para uma trilha");
        }
        boolean temDescricao = acao.descricao() != null && !acao.descricao().isBlank();
        if (!temDescricao) {
            return erro(context, "descricao", "descrição é obrigatória para ação do tipo " + acao.tipo());
        }
        return acao.prazo() != null || erro(context, "prazo", "prazo é obrigatório");
    }

    private static boolean erro(ConstraintValidatorContext context, String campo, String mensagem) {
        context.buildConstraintViolationWithTemplate(mensagem)
                .addPropertyNode(campo)
                .addConstraintViolation();
        return false;
    }
}
