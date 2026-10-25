package com.nexo.service.recomendacao;

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
import com.nexo.repository.PdiItemRepository;
import com.nexo.repository.PdiRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lê do banco, numa <strong>transação curta</strong>, tudo o que a sugestão de
 * trilhas precisa (issue #63).
 *
 * <p>Existe para separar o acesso ao banco da chamada ao modelo. O
 * {@code SugestaoDeTrilhasService} chama este coletor, a transação fecha, e só
 * então o modelo é consultado — a chamada pode levar
 * {@code IA_TIMEOUT} × {@code IA_MAX_TENTATIVAS} e não pode segurar uma
 * conexão do pool nesse tempo. É a mesma regra que o
 * {@code ConsultaAssistenteService} segue no fluxo de perguntas.</p>
 *
 * <p>Por isso tudo sai daqui como record desligado do JPA
 * ({@link TrilhaCandidata}, {@link PerfilDoUsuario}): nada de lazy loading
 * depois que a sessão do Hibernate fecha.</p>
 *
 * <p>Singleton sem estado.</p>
 */
@Component
public class ColetorDeDadosDaSugestao {

    private final TrilhaRepository trilhaRepository;
    private final TrilhaAtribuicaoRepository atribuicaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PdiRepository pdiRepository;
    private final PdiItemRepository pdiItemRepository;
    private final int catalogoMaximo;

    public ColetorDeDadosDaSugestao(TrilhaRepository trilhaRepository,
                                    TrilhaAtribuicaoRepository atribuicaoRepository,
                                    UsuarioRepository usuarioRepository,
                                    PdiRepository pdiRepository,
                                    PdiItemRepository pdiItemRepository,
                                    IaProperties properties) {
        this.trilhaRepository = trilhaRepository;
        this.atribuicaoRepository = atribuicaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.pdiRepository = pdiRepository;
        this.pdiItemRepository = pdiItemRepository;
        this.catalogoMaximo = properties.sugestao().catalogoMaximo();
    }

    @Transactional(readOnly = true)
    public DadosParaSugestao coletar(UsuarioAutenticado requisitante) {
        Usuario usuario = usuarioRepository.findById(requisitante.getId()).orElse(null);
        Area area = usuario == null ? null : usuario.getArea();
        Cargo cargo = usuario == null ? null : usuario.getCargo();

        Set<UUID> concluidas = new HashSet<>();
        List<String> nomesConcluidos = new ArrayList<>();
        List<String> nomesEmAndamento = new ArrayList<>();
        for (TrilhaAtribuicao atribuicao : atribuicaoRepository
                .findByTenantIdAndUsuarioId(requisitante.getTenantId(), requisitante.getId())) {
            Trilha trilha = atribuicao.getTrilha();
            if (atribuicao.getStatus() == StatusAtribuicao.CONCLUIDA) {
                concluidas.add(trilha.getId());
                nomesConcluidos.add(trilha.getNome());
            } else if (atribuicao.getStatus() != StatusAtribuicao.NAO_INICIADA) {
                nomesEmAndamento.add(trilha.getNome());
            }
        }

        EstrategiaRecomendacao.Contexto contexto = new EstrategiaRecomendacao.Contexto(
                requisitante.getTenantId(), requisitante.getId(), area == null ? null : area.getId());
        PerfilDoUsuario perfil = new PerfilDoUsuario(
                area == null ? null : area.getNome(),
                cargo == null ? null : cargo.getNome(),
                nomesConcluidos,
                nomesEmAndamento,
                competenciasDoPdi(requisitante));
        return new DadosParaSugestao(contexto, perfil, catalogo(requisitante.getTenantId(), concluidas));
    }

    /**
     * Trilhas ativas do tenant que o usuário ainda não concluiu, em ordem de
     * nome e limitadas a {@code IA_SUGESTAO_CATALOGO_MAXIMO} — sugerir o que a
     * pessoa já fez não ajuda, e um prompt sem teto cresce sem limite.
     */
    private List<TrilhaCandidata> catalogo(UUID tenantId, Set<UUID> concluidas) {
        return trilhaRepository.findByTenantIdAndAtivoTrueOrderByNome(tenantId).stream()
                .filter(trilha -> !concluidas.contains(trilha.getId()))
                .limit(catalogoMaximo)
                .map(TrilhaCandidata::de)
                .toList();
    }

    /** Competências do PDI ativo mais recente, quando o colaborador tem um. */
    private List<String> competenciasDoPdi(UsuarioAutenticado requisitante) {
        Pdi pdi = pdiRepository.findFirstByTenantIdAndColaboradorIdAndStatusOrderByInicioDescCriadoEmDesc(
                        requisitante.getTenantId(), requisitante.getId(), StatusPdi.ATIVO)
                .orElse(null);
        if (pdi == null) {
            return List.of();
        }
        return pdiItemRepository.findByPdiIdOrderByCompetenciaNomeAsc(pdi.getId()).stream()
                .map(ColetorDeDadosDaSugestao::descrever)
                .toList();
    }

    private static String descrever(PdiItem item) {
        return item.getCompetencia().getNome() + " (nível " + item.getNivelAtual()
                + " para " + item.getNivelAlvo() + ")";
    }
}
