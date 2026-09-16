package com.nexo.dto.usuario;

import com.nexo.entity.Area;
import com.nexo.entity.Cargo;
import com.nexo.entity.Perfil;
import com.nexo.entity.SituacaoUsuario;
import com.nexo.entity.Usuario;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dados de saída de um usuário. Nunca inclui senha ou hash, e nunca expõe
 * {@code Cargo}/{@code Area} como entidade JPA — apenas o resumo
 * {id, nome} necessário para o front exibir/selecionar.
 */
public record UsuarioResponse(
        UUID id,
        String nomeCompleto,
        String email,
        Recurso cargo,
        Recurso area,
        Perfil papel,
        SituacaoUsuario situacao,
        LocalDate dataEntrada,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm) {

    /** Resumo de um recurso de catálogo (Cargo ou Área) — nunca a entidade completa. */
    public record Recurso(UUID id, String nome) {
    }

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNomeCompleto(),
                usuario.getEmail(),
                recursoDe(usuario.getCargo()),
                recursoDe(usuario.getArea()),
                usuario.getPapel(),
                usuario.getSituacao(),
                usuario.getDataEntrada(),
                usuario.getCriadoEm(),
                usuario.getAtualizadoEm());
    }

    private static Recurso recursoDe(Cargo cargo) {
        return cargo == null ? null : new Recurso(cargo.getId(), cargo.getNome());
    }

    private static Recurso recursoDe(Area area) {
        return area == null ? null : new Recurso(area.getId(), area.getNome());
    }
}
