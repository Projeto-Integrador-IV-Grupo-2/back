package com.nexo.dto.area;

import com.nexo.entity.Area;
import java.util.UUID;

/**
 * Dados de saída de uma Área. Não expõe o tenant: ele nunca varia dentro de
 * uma requisição, já que toda operação é restrita ao tenant do usuário
 * autenticado.
 *
 * @param id    identificador da área
 * @param nome  nome exibido
 * @param ativo situação da área (inativa não deve ser oferecida em novos cadastros)
 */
public record AreaResponse(UUID id, String nome, boolean ativo) {

    public static AreaResponse from(Area area) {
        return new AreaResponse(area.getId(), area.getNome(), area.isAtivo());
    }
}
