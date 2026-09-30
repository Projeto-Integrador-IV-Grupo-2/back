package com.nexo.repository;

import com.nexo.entity.Perfil;
import com.nexo.entity.SituacaoUsuario;
import java.util.UUID;

/**
 * Filtros de {@code GET /api/usuarios}, todos opcionais e combinados com E.
 *
 * @param departamento trecho do nome da área (a tela de usuários busca por texto, não por id)
 */
public record UsuarioFiltro(
        String nome,
        String email,
        UUID areaId,
        Perfil papel,
        SituacaoUsuario situacao,
        String departamento
) {

    public static UsuarioFiltro vazio() {
        return new UsuarioFiltro(null, null, null, null, null, null);
    }
}
