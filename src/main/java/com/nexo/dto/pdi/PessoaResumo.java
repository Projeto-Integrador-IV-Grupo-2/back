package com.nexo.dto.pdi;

import com.nexo.entity.Usuario;
import java.util.UUID;

/** Colaborador ou gestor de um PDI — só o necessário para exibir. */
public record PessoaResumo(UUID id, String nomeCompleto) {

    public static PessoaResumo de(Usuario usuario) {
        return new PessoaResumo(usuario.getId(), usuario.getNomeCompleto());
    }
}
