package com.nexo.service.recomendacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.config.IaProperties;
import com.nexo.entity.Area;
import com.nexo.entity.Cargo;
import com.nexo.entity.Pdi;
import com.nexo.entity.PdiItem;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.StatusPdi;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.MensagemIa;
import com.nexo.repository.CriterioRecomendacao;
import com.nexo.repository.PdiItemRepository;
import com.nexo.repository.PdiRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.UsuarioRepository;
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
 * usuário ficam de fora do prompt — ver
 * {@link PromptDeSugestao.PerfilParaSugestao}.</p>
 *
 * <p>Na listagem ({@code ?recomendar=IA}), {@link #prioridade} usa apenas o
 * que já está no {@link CacheDeSugestoes}: ordenar uma página nunca chama o
 * modelo nem segura a transação esperando a rede.</p>
 *
 * <p>Singleton sem estado.</p>
 */
@Component
public class RecomendacaoPorIa implements EstrategiaRecomendacao {

    /** Usado quando o modelo escolhe a trilha mas esquece de escrever o porquê. */
    static final String MOTIVO_PADRAO = "Indicada pela IA com base no seu perfil.";

    private final UsuarioRepository usuarioRepository;
    private final TrilhaAtribuicaoRepository atribuicaoRepository;
    private final PdiRepository pdiRepository;
    private final PdiItemRepository pdiItemRepository;
    private final ClienteIa clienteIa;
    private final ObjectMapper objectMapper;
    private final CacheDeSugestoes cache;
    private final int catalogoMaximo;

    public RecomendacaoPorIa(UsuarioRepository usuarioRepository,
                             TrilhaAtribuicaoRepository atribuicaoRepository,
                             PdiRepository pdiRepository,
                             PdiItemRepository pdiItemRepository,
                             ClienteIa clienteIa,
                             ObjectMapper objectMapper,
                             CacheDeSugestoes cache,
                             IaProperties properties) {
        this.usuarioRepository = usuarioRepository;
        this.atribuicaoRepository = atribuicaoRepository;
        this.pdiRepository = pdiRepository;
        this.pdiItemRepository = pdiItemRepository;
        this.clienteIa = clienteIa;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.catalogoMaximo = properties.sugestao().catalogoMaximo();
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
    public List<TrilhaSugerida> sugerir(Contexto contexto, List<Trilha> catalogo, int limite) {
        if (limite <= 0 || catalogo.isEmpty()) {
            return List.of();
        }
        List<Trilha> candidatas = catalogo.size() <= catalogoMaximo
                ? catalogo
                : catalogo.subList(0, catalogoMaximo);
        String turno = PromptDeSugestao.turno(perfilDe(contexto), candidatas, limite);
        String resposta = clienteIa.gerarResposta(PromptDeSugestao.PROMPT_SISTEMA,
                List.of(MensagemIa.doUsuario(turno))).texto();

        List<UUID> permitidos = candidatas.stream().map(Trilha::getId).toList();
        Map<UUID, String> motivos = PromptDeSugestao.motivosPorTrilha(objectMapper, resposta, permitidos, limite);
        List<TrilhaSugerida> sugeridas = new ArrayList<>(motivos.size());
        motivos.forEach((id, motivo) -> candidatas.stream()
                .filter(trilha -> trilha.getId().equals(id))
                .findFirst()
                .ifPresent(trilha -> sugeridas.add(
                        new TrilhaSugerida(trilha, motivo == null ? MOTIVO_PADRAO : motivo))));
        return List.copyOf(sugeridas);
    }

    private PromptDeSugestao.PerfilParaSugestao perfilDe(Contexto contexto) {
        Usuario usuario = usuarioRepository.findById(contexto.usuarioId()).orElse(null);
        Area area = usuario == null ? null : usuario.getArea();
        Cargo cargo = usuario == null ? null : usuario.getCargo();

        List<String> concluidas = new ArrayList<>();
        List<String> emAndamento = new ArrayList<>();
        for (TrilhaAtribuicao atribuicao : atribuicaoRepository
                .findByTenantIdAndUsuarioId(contexto.tenantId(), contexto.usuarioId())) {
            String nome = atribuicao.getTrilha().getNome();
            if (atribuicao.getStatus() == StatusAtribuicao.CONCLUIDA) {
                concluidas.add(nome);
            } else if (atribuicao.getStatus() != StatusAtribuicao.NAO_INICIADA) {
                emAndamento.add(nome);
            }
        }

        return new PromptDeSugestao.PerfilParaSugestao(
                area == null ? null : area.getNome(),
                cargo == null ? null : cargo.getNome(),
                concluidas,
                emAndamento,
                competenciasDoPdi(contexto));
    }

    /** Competências do PDI ativo mais recente, quando o colaborador tem um. */
    private List<String> competenciasDoPdi(Contexto contexto) {
        Pdi pdi = pdiRepository.findFirstByTenantIdAndColaboradorIdAndStatusOrderByInicioDescCriadoEmDesc(
                        contexto.tenantId(), contexto.usuarioId(), StatusPdi.ATIVO)
                .orElse(null);
        if (pdi == null) {
            return List.of();
        }
        return pdiItemRepository.findByPdiIdOrderByCompetenciaNomeAsc(pdi.getId()).stream()
                .map(RecomendacaoPorIa::descrever)
                .toList();
    }

    private static String descrever(PdiItem item) {
        return item.getCompetencia().getNome() + " (nível " + item.getNivelAtual()
                + " para " + item.getNivelAlvo() + ")";
    }
}
