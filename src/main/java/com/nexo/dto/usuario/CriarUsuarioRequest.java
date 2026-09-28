package com.nexo.dto.usuario;

import com.nexo.dto.usuario.validation.SenhaForte;
import com.nexo.entity.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Dados de entrada para o cadastro de um novo usuário administrativo.
 *
 * <p>O usuário é sempre criado no mesmo tenant do administrador autenticado
 * que faz a chamada — não há campo de tenant aqui, de propósito, para que o
 * cliente nunca possa cadastrar em um tenant diferente do seu.</p>
 *
 * @param nomeCompleto nome completo (obrigatório)
 * @param email        e-mail — único dentro do tenant (obrigatório, formato validado)
 * @param senha        senha em texto puro — nunca persistida ou logada como tal;
 *                     é convertida em hash BCrypt pelo {@code UsuarioService} antes
 *                     de qualquer persistência (obrigatória; política mínima:
 *                     8+ caracteres com maiúscula, minúscula e número)
 * @param papel        perfil de acesso (obrigatório)
 * @param cargoId      id do cargo, cadastrado previamente no catálogo do tenant (opcional)
 * @param areaId       id da área/departamento, cadastrado previamente no catálogo do tenant (opcional)
 * @param dataEntrada  data de admissão/entrada na empresa (obrigatória; não pode ser futura)
 */
public record CriarUsuarioRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres")
        String nomeCompleto,

        @NotBlank(message = "e-mail é obrigatório")
        @Email(message = "e-mail em formato inválido")
        @Size(max = 255, message = "e-mail deve ter no máximo 255 caracteres")
        String email,

        @NotBlank(message = "senha é obrigatória")
        @Size(min = 8, max = 72, message = "senha deve ter entre 8 e 72 caracteres")
        @SenhaForte
        String senha,

        @NotNull(message = "perfil é obrigatório")
        Perfil papel,

        UUID cargoId,

        UUID areaId,

        @NotNull(message = "data de entrada é obrigatória")
        @PastOrPresent(message = "data de entrada não pode ser no futuro")
        LocalDate dataEntrada) {
}
