package com.nexo.service.recomendacao;

import com.nexo.config.IaProperties;
import com.nexo.dto.trilha.SugestaoTrilhaResponse;
import com.nexo.dto.trilha.SugestoesDeTrilhaResponse;
import com.nexo.entity.Area;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.exception.IaIndisponivelException;
import com.nexo.repository.CriterioRecomendacao;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trilhas indicadas pela IA para o usuário autenticado (issue #63).
 *
 * <p>Monta o catálogo de candidatas — trilhas <strong>ativas do tenant que o
 * usuário ainda não concluiu</strong> —, pede a indicação à estratégia de IA
 * e devolve até {@code IA_SUGESTAO_MAXIMO} trilhas, cada uma com um motivo.
 * Como o catálogo é montado aqui e a estratégia só pode devolver ids que
 * estavam nele, nenhuma trilha de outra empresa entra no prompt nem na
 * resposta.</p>
 *
 * <p><strong>Com a IA fora do ar</strong> — ou com uma resposta que não deu
 * para aproveitar — a resposta continua **200**, com a recomendação por área
 * e o aviso de que as sugestões não vieram da IA. Quem decide isso é a
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

    private final TrilhaRepository trilhaRepository;
    private final TrilhaAtribuicaoRepository atribuicaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstrategiaRecomendacaoFactory recomendacoes;
    private final CacheDeSugestoes cache;
    private final int maximo;

    public SugestaoDeTrilhasService(TrilhaRepository trilhaRepository,
                                    TrilhaAtribuicaoRepository atribuicaoRepository,
                                    UsuarioRepository usuarioRepository,
                                    EstrategiaRecomendacaoFactory recomendacoes,
                                    CacheDeSugestoes cache,
                                    IaProperties properties) {
        this.trilhaRepository = trilhaRepository;
        this.atribuicaoRepository = atribuicaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.recomendacoes = recomendacoes;
        this.cache = cache;
        this.maximo = properties.sugestao().maximo();
    }

    @Transactional(readOnly = true)
    public SugestoesDeTrilhaResponse sugerir(UsuarioAutenticado requisitante) {
        return cache.buscar(requisitante.getTenantId(), requisitante.getId())
                .orElseGet(() -> calcularEGuardar(requisitante));
    }

    private SugestoesDeTrilhaResponse calcularEGuardar(UsuarioAutenticado requisitante) {
        EstrategiaRecomendacao.Contexto contexto = new EstrategiaRecomendacao.Contexto(
                requisitante.getTenantId(), requisitante.getId(), areaDoUsuario(requisitante));
        List<Trilha> catalogo = catalogoDeCandidatas(contexto);
        if (catalogo.isEmpty()) {
            // Nada a indicar: não há o que perguntar ao modelo.
            return SugestoesDeTrilhaResponse.daIa(List.of());
        }

        SugestoesDeTrilhaResponse resposta = comIa(contexto, catalogo)
                .orElseGet(() -> SugestoesDeTrilhaResponse.daArea(
                        converter(recomendacoes.para(CriterioRecomendacao.AREA)
                                .sugerir(contexto, catalogo, maximo))));
        cache.guardar(requisitante.getTenantId(), requisitante.getId(), resposta);
        return resposta;
    }

    /** Vazio quando o provedor caiu ou quando a resposta dele não rendeu nenhuma trilha válida. */
    private Optional<SugestoesDeTrilhaResponse> comIa(EstrategiaRecomendacao.Contexto contexto,
                                                                List<Trilha> catalogo) {
        try {
            List<TrilhaSugerida> sugeridas = recomendacoes.para(CriterioRecomendacao.IA)
                    .sugerir(contexto, catalogo, maximo);
            return sugeridas.isEmpty()
                    ? Optional.empty()
                    : Optional.of(SugestoesDeTrilhaResponse.daIa(converter(sugeridas)));
        } catch (IaIndisponivelException ex) {
            // Nunca derruba o endpoint: a tela mostra a recomendação por área com o aviso.
            log.warn("Sugestão de trilhas sem IA para o tenant {}: {}", contexto.tenantId(), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Trilhas ativas do tenant que o usuário ainda não concluiu, em ordem de
     * nome. Trilha concluída sai: sugerir o que a pessoa já fez não ajuda.
     */
    private List<Trilha> catalogoDeCandidatas(EstrategiaRecomendacao.Contexto contexto) {
        Set<UUID> concluidas = new HashSet<>();
        for (TrilhaAtribuicao atribuicao : atribuicaoRepository
                .findByTenantIdAndUsuarioId(contexto.tenantId(), contexto.usuarioId())) {
            if (atribuicao.getStatus() == StatusAtribuicao.CONCLUIDA) {
                concluidas.add(atribuicao.getTrilha().getId());
            }
        }
        return trilhaRepository.findByTenantIdAndAtivoTrueOrderByNome(contexto.tenantId()).stream()
                .filter(trilha -> !concluidas.contains(trilha.getId()))
                .toList();
    }

    private UUID areaDoUsuario(UsuarioAutenticado requisitante) {
        return usuarioRepository.findById(requisitante.getId())
                .map(Usuario::getArea)
                .map(Area::getId)
                .orElse(null);
    }

    private static List<SugestaoTrilhaResponse> converter(List<TrilhaSugerida> sugeridas) {
        return sugeridas.stream()
                .map(sugerida -> SugestaoTrilhaResponse.de(sugerida.trilha(), sugerida.motivo()))
                .toList();
    }
}
