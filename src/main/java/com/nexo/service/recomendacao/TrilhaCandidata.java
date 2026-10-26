package com.nexo.service.recomendacao;

import com.nexo.entity.Area;
import com.nexo.entity.Trilha;
import java.util.UUID;

/**
 * Retrato de uma trilha candidata a ser sugerida, já desligado do JPA.
 *
 * <p>Existe para a chamada ao modelo acontecer <strong>fora</strong> de
 * qualquer transação: a entidade {@link Trilha} traz a área em lazy loading e
 * explodiria ao ser lida com a sessão do Hibernate fechada. Este record é
 * montado dentro da transação curta do
 * {@link ColetorDeDadosDaSugestao} e carrega tudo o que o prompt e a resposta
 * precisam.</p>
 */
public record TrilhaCandidata(UUID id, String nome, UUID areaId, String areaNome, String objetivos) {

    public static TrilhaCandidata de(Trilha trilha) {
        Area area = trilha.getArea();
        return new TrilhaCandidata(
                trilha.getId(),
                trilha.getNome(),
                area == null ? null : area.getId(),
                area == null ? null : area.getNome(),
                trilha.getObjetivos());
    }
}
