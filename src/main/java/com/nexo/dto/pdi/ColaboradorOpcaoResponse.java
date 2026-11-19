package com.nexo.dto.pdi;

import com.nexo.entity.Area;
import com.nexo.entity.Usuario;
import java.util.UUID;

/**
 * Opção de colaborador nos campos de escolha — ao criar um PDI e ao atribuir
 * uma trilha. Só o necessário para reconhecer a pessoa.
 *
 * @param area área do colaborador; {@code null} quando ele não tem
 */
public record ColaboradorOpcaoResponse(UUID id, String nomeCompleto, String email, AreaResumo area) {

    public record AreaResumo(UUID id, String nome) {
    }

    public static ColaboradorOpcaoResponse de(Usuario usuario) {
        Area area = usuario.getArea();
        return new ColaboradorOpcaoResponse(usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail(),
                area == null ? null : new AreaResumo(area.getId(), area.getNome()));
    }
}
