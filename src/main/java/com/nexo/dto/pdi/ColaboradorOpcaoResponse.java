package com.nexo.dto.pdi;

import com.nexo.entity.Usuario;
import java.util.UUID;

/** Opção do campo "Colaborador" ao criar um PDI — só o necessário para escolher. */
public record ColaboradorOpcaoResponse(UUID id, String nomeCompleto, String email) {

    public static ColaboradorOpcaoResponse de(Usuario usuario) {
        return new ColaboradorOpcaoResponse(usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail());
    }
}
