package com.nexo.dto.empresa;

import com.nexo.dto.usuario.validation.SenhaForte;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de uma nova empresa (tenant) com o seu primeiro administrador.
 *
 * <p>Só a equipe da plataforma (administradores do tenant {@code nexo}) usa
 * este pedido. O administrador criado aqui entra na empresa nova e, a partir
 * dele, monta áreas, usuários, conteúdos e documentos pela própria tela.</p>
 *
 * @param nome                nome da empresa exibido no sistema
 * @param subdominio          identificador curto da empresa, usado no login quando o e-mail se repete
 * @param cnpj                CNPJ, opcional
 * @param nomeAdministrador   nome do primeiro administrador
 * @param emailAdministrador  e-mail de login do primeiro administrador
 * @param senhaAdministrador  senha inicial do administrador (vira hash BCrypt; nunca é gravada em texto)
 */
public record CriarEmpresaRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres")
        String nome,

        @NotBlank(message = "subdomínio é obrigatório")
        @Size(min = 2, max = 63, message = "subdomínio deve ter entre 2 e 63 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$",
                message = "subdomínio aceita só letras, números e hífen (sem hífen nas pontas)")
        String subdominio,

        @Size(max = 18, message = "CNPJ deve ter no máximo 18 caracteres")
        String cnpj,

        @NotBlank(message = "nome do administrador é obrigatório")
        @Size(max = 150, message = "nome do administrador deve ter no máximo 150 caracteres")
        String nomeAdministrador,

        @NotBlank(message = "e-mail do administrador é obrigatório")
        @Email(message = "e-mail em formato inválido")
        @Size(max = 255, message = "e-mail deve ter no máximo 255 caracteres")
        String emailAdministrador,

        @NotBlank(message = "senha é obrigatória")
        @Size(min = 8, max = 72, message = "senha deve ter entre 8 e 72 caracteres")
        @SenhaForte
        String senhaAdministrador) {
}
