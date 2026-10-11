package com.nexo.dto.area;

import com.nexo.entity.Area;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dados de saída de uma Área. Não expõe o tenant: ele nunca varia dentro de
 * uma requisição, já que toda operação é restrita ao tenant do usuário
 * autenticado.
 *
 * @param id             identificador da área
 * @param nome           nome exibido
 * @param descricao      texto livre (pode ser nulo)
 * @param ativo          situação da área (inativa não deve ser oferecida em novos cadastros)
 * @param totalPessoas   usuários vinculados à área ({@code usuario.area_id})
 * @param totalConteudos conteúdos vinculados à área ({@code conteudo.area_id})
 * @param criadoEm       auditoria
 * @param atualizadoEm   auditoria
 */
public record AreaResponse(
        UUID id,
        String nome,
        String descricao,
        boolean ativo,
        long totalPessoas,
        long totalConteudos,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm) {

    public static AreaResponse from(Area area, long totalPessoas, long totalConteudos) {
        return new AreaResponse(
                area.getId(),
                area.getNome(),
                area.getDescricao(),
                area.isAtivo(),
                totalPessoas,
                totalConteudos,
                area.getCriadoEm(),
                area.getAtualizadoEm());
    }
}
