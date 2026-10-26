package com.nexo.service.recomendacao;

import com.nexo.config.IaProperties;
import com.nexo.dto.trilha.SugestaoTrilhaResponse;
import com.nexo.dto.trilha.SugestoesDeTrilhaResponse;
import com.nexo.exception.IaIndisponivelException;
import com.nexo.repository.CriterioRecomendacao;
import com.nexo.security.UsuarioAutenticado;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Trilhas indicadas pela IA para o usuário autenticado (issue #63).
 *
 * <p>O {@link ColetorDeDadosDaSugestao} monta, numa transação curta, o
 * catálogo de candidatas — trilhas <strong>ativas do tenant que o usuário
 * ainda não concluiu</strong> — e o perfil dele. Como o catálogo é montado lá
 * e a estratégia só pode devolver ids que estavam nele, nenhuma trilha de
 * outra empresa entra no prompt nem na resposta.</p>
 *
 * <p><strong>Sem {@code @Transactional} de propósito:</strong> a chamada ao
 * modelo pode levar {@code IA_TIMEOUT} × {@code IA_MAX_TENTATIVAS} e não pode
 * segurar conexão do banco nesse tempo. O banco é lido antes, a transação
 * fecha, e só então o modelo é consultado — a mesma regra que o
 * {@code ConsultaAssistenteService} segue no fluxo de perguntas.</p>
 *
 * <p><strong>Com a IA fora do ar</strong> — ou com uma resposta que não deu
 * para aproveitar — a resposta continua 200, com a recomendação por área e o
 * aviso de que as sugestões não vieram da IA. Quem decide isso é a
 * {@link EstrategiaRecomendacaoFactory}: as duas formas de recomendar são
 * estratégias do mesmo tipo, e trocar uma pela outra é escolher outro
 * critério.</p>
 *
 * <p>O resultado fica guardado por usuário no {@link CacheDeSugestoes}, então
 * dois acessos seguidos chamam o modelo uma vez só. Iniciar ou concluir uma
 * trilha invalida o que estava guardado
 * ({@code SugestoesDeTrilhaListener}).</p>
 *
 * <p>Singleton sem estado — o estado vive no cache, que também é bean único.</p>
 */
@Service
public class SugestaoDeTrilhasService {

    private static final Logger log = LoggerFactory.getLogger(SugestaoDeTrilhasService.class);

    private final ColetorDeDadosDaSugestao coletor;
    private final EstrategiaRecomendacaoFactory recomendacoes;
    private final CacheDeSugestoes cache;
    private final int maximo;

    public SugestaoDeTrilhasService(ColetorDeDadosDaSugestao coletor,
                                    EstrategiaRecomendacaoFactory recomendacoes,
                                    CacheDeSugestoes cache,
                                    IaProperties properties) {
        this.coletor = coletor;
        this.recomendacoes = recomendacoes;
        this.cache = cache;
        this.maximo = properties.sugestao().maximo();
    }

    public SugestoesDeTrilhaResponse sugerir(UsuarioAutenticado requisitante) {
        return cache.buscar(requisitante.getTenantId(), requisitante.getId())
                .orElseGet(() -> calcularEGuardar(requisitante));
    }

    private SugestoesDeTrilhaResponse calcularEGuardar(UsuarioAutenticado requisitante) {
        // Tudo o que vem do banco é lido aqui, numa transação que fecha antes
        // de o modelo ser chamado.
        DadosParaSugestao dados = coletor.coletar(requisitante);
        if (dados.catalogo().isEmpty()) {
            // Nada a indicar: não há o que perguntar ao modelo.
            return SugestoesDeTrilhaResponse.daIa(List.of());
        }

        SugestoesDeTrilhaResponse resposta = comIa(dados)
                .orElseGet(() -> SugestoesDeTrilhaResponse.daArea(
                        converter(recomendacoes.para(CriterioRecomendacao.AREA).sugerir(dados, maximo))));
        cache.guardar(requisitante.getTenantId(), requisitante.getId(), resposta);
        return resposta;
    }

    /** Vazio quando o provedor caiu ou quando a resposta dele não rendeu nenhuma trilha válida. */
    private Optional<SugestoesDeTrilhaResponse> comIa(DadosParaSugestao dados) {
        try {
            List<TrilhaSugerida> sugeridas = recomendacoes.para(CriterioRecomendacao.IA)
                    .sugerir(dados, maximo);
            return sugeridas.isEmpty()
                    ? Optional.empty()
                    : Optional.of(SugestoesDeTrilhaResponse.daIa(converter(sugeridas)));
        } catch (IaIndisponivelException ex) {
            // Nunca derruba o endpoint: a tela mostra a recomendação por área com o aviso.
            log.warn("Sugestão de trilhas sem IA para o tenant {}: {}",
                    dados.contexto().tenantId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private static List<SugestaoTrilhaResponse> converter(List<TrilhaSugerida> sugeridas) {
        return sugeridas.stream()
                .map(sugerida -> SugestaoTrilhaResponse.de(sugerida.trilha(), sugerida.motivo()))
                .toList();
    }
}
