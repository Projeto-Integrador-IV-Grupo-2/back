package com.nexo.service.recomendacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.entity.Trilha;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.MensagemIa;
import com.nexo.repository.CriterioRecomendacao;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sugestão de trilhas pela IA (issue #63).
 *
 * <p>O modelo recebe o perfil do colaborador — área, cargo, o que ele já
 * estudou e as competências do PDI — e o catálogo de trilhas candidatas, e
 * devolve ids com um motivo cada. <strong>Só id que estava no catálogo
 * enviado entra na resposta</strong>: id inventado é descartado, então nunca
 * aparece trilha de outra empresa, inativa ou já concluída.</p>
 *
 * <p>Nenhum dado pessoal vai para o modelo: nome, e-mail e identificadores do
 * usuário ficam de fora do prompt — ver {@link PerfilDoUsuario}.</p>
 *
 * <p><strong>Esta classe não conhece repository nenhum, de propósito.</strong>
 * Ela roda fora de transação, com os dados já lidos pelo
 * {@link ColetorDeDadosDaSugestao}, para a chamada ao modelo não segurar
 * conexão do banco. Receber só records desligados do JPA é o que garante
 * isso: não há lazy loading possível aqui.</p>
 *
 * <p>Na listagem ({@code ?recomendar=IA}), {@link #prioridade} usa apenas o
 * que já está no {@link CacheDeSugestoes}: ordenar uma página nunca chama o
 * modelo nem espera a rede.</p>
 *
 * <p>Singleton sem estado.</p>
 */
@Component
public class RecomendacaoPorIa implements EstrategiaRecomendacao {

    /** Usado quando o modelo escolhe a trilha mas esquece de escrever o porquê. */
    static final String MOTIVO_PADRAO = "Indicada pela IA com base no seu perfil.";

    private final ClienteIa clienteIa;
    private final ObjectMapper objectMapper;
    private final CacheDeSugestoes cache;

    public RecomendacaoPorIa(ClienteIa clienteIa, ObjectMapper objectMapper, CacheDeSugestoes cache) {
        this.clienteIa = clienteIa;
        this.objectMapper = objectMapper;
        this.cache = cache;
    }

    @Override
    public CriterioRecomendacao criterio() {
        return CriterioRecomendacao.IA;
    }

    @Override
    public Expression<Integer> prioridade(Root<Trilha> trilha, CriteriaBuilder cb, Contexto contexto) {
        List<UUID> sugeridas = cache.idsGuardados(contexto.tenantId(), contexto.usuarioId());
        if (sugeridas.isEmpty()) {
            return null;
        }
        CriteriaBuilder.Case<Integer> ordem = cb.selectCase();
        for (int posicao = 0; posicao < sugeridas.size(); posicao++) {
            ordem = ordem.when(cb.equal(trilha.get("id"), sugeridas.get(posicao)), posicao);
        }
        return ordem.otherwise(sugeridas.size());
    }

    /**
     * @throws com.nexo.exception.IaIndisponivelException provedor fora do ar —
     *         quem chama cai na recomendação por área
     */
    @Override
    public List<TrilhaSugerida> sugerir(DadosParaSugestao dados, int limite) {
        List<TrilhaCandidata> catalogo = dados.catalogo();
        if (limite <= 0 || catalogo.isEmpty()) {
            return List.of();
        }
        String turno = PromptDeSugestao.turno(dados.perfil(), catalogo, limite);
        String resposta = clienteIa.gerarResposta(PromptDeSugestao.PROMPT_SISTEMA,
                List.of(MensagemIa.doUsuario(turno))).texto();

        List<UUID> permitidos = catalogo.stream().map(TrilhaCandidata::id).toList();
        Map<UUID, String> motivos = PromptDeSugestao.motivosPorTrilha(objectMapper, resposta, permitidos, limite);
        List<TrilhaSugerida> sugeridas = new ArrayList<>(motivos.size());
        motivos.forEach((id, motivo) -> catalogo.stream()
                .filter(trilha -> trilha.id().equals(id))
                .findFirst()
                .ifPresent(trilha -> sugeridas.add(
                        new TrilhaSugerida(trilha, motivo == null ? MOTIVO_PADRAO : motivo))));
        return List.copyOf(sugeridas);
    }
}
