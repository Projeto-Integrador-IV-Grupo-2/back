package com.nexo.dto.usuario;

import com.nexo.entity.Perfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Dados de entrada para edição de um usuário existente.
 *
 * <p>Não permite alterar e-mail ou senha (fluxos próprios, fora do escopo
 * deste card) nem a situação (alterada exclusivamente via
 * {@code PATCH /api/usuarios/{id}/situacao}, por {@code AtualizarSituacaoRequest}).
 * {@code cargoId}/{@code areaId} nulos limpam a associação atual.</p>
 */
public record AtualizarUsuarioRequest(
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 150, message = "nome deve ter no máximo 150 caracteres")
        String nomeCompleto,

        UUID cargoId,

        UUID areaId,

        @NotNull(message = "perfil é obrigatório")
        Perfil papel) {
}
