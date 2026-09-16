package com.nexo.repository;

import com.nexo.entity.Perfil;
import com.nexo.entity.SituacaoUsuario;
import java.util.UUID;

public record UsuarioFiltro(
        String nome,
        String email,
        UUID areaId,
        Perfil papel,
        SituacaoUsuario situacao
) {

    public static UsuarioFiltro vazio() {
        return new UsuarioFiltro(null, null, null, null, null);
    }
}
