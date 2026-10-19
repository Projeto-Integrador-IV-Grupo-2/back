package com.nexo.dto;

import com.nexo.entity.HistoricoInteracao;
import com.nexo.entity.TipoInteracaoHistorico;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Representação pública do histórico; nunca retorna a entidade JPA. */
public record HistoricoInteracaoResponse(
        UUID id,
        TipoInteracaoHistorico tipo,
        Map<String, Object> detalhes,
        UsuarioResumo usuario,
        OffsetDateTime criadoEm) {

    public record UsuarioResumo(UUID id, String nomeCompleto, String email) {
    }

    public static HistoricoInteracaoResponse de(HistoricoInteracao historico) {
        var usuario = historico.getUsuario();
        return new HistoricoInteracaoResponse(
                historico.getId(),
                historico.getTipo(),
                Map.copyOf(historico.getDetalhes()),
                new UsuarioResumo(usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail()),
                historico.getCriadoEm());
    }
}
