package com.nexo.dto.senha;

import com.nexo.dto.usuario.validation.SenhaForte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/auth/redefinir-senha}. A nova senha segue a mesma
 * regra do cadastro ({@link SenhaForte}).
 *
 * @param token     o valor que veio no link do e-mail
 * @param novaSenha a senha nova
 */
public record RedefinirSenhaRequest(
        @NotBlank(message = "O link de redefinição está incompleto")
        @Size(max = 200, message = "O link de redefinição é inválido")
        String token,

        @NotBlank(message = "Informe a nova senha")
        @Size(max = 100, message = "Senha deve ter no máximo 100 caracteres")
        @SenhaForte
        String novaSenha) {

    /** Esconde o token e a senha, para nenhum dos dois aparecer num log. */
    @Override
    public String toString() {
        return "RedefinirSenhaRequest[***]";
    }
}
