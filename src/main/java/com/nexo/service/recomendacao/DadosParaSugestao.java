package com.nexo.service.recomendacao;

import java.util.List;

/**
 * Tudo o que uma {@link EstrategiaRecomendacao} precisa para sugerir, já lido
 * do banco e desligado do JPA.
 *
 * <p>É o pacote que o {@link ColetorDeDadosDaSugestao} monta numa transação
 * curta para a chamada ao modelo acontecer fora dela.</p>
 *
 * @param catalogo trilhas candidatas — ativas, do tenant e não concluídas pelo
 *                 usuário. A estratégia só pode devolver trilhas daqui.
 */
public record DadosParaSugestao(EstrategiaRecomendacao.Contexto contexto, PerfilDoUsuario perfil,
                                List<TrilhaCandidata> catalogo) {

    public DadosParaSugestao {
        catalogo = catalogo == null ? List.of() : List.copyOf(catalogo);
    }
}
