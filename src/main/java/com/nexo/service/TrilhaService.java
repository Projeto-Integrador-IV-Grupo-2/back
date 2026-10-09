package com.nexo.service;

import com.nexo.dto.trilha.CriarTrilhaRequest;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.dto.trilha.TrilhaDetalheResponse;
import com.nexo.dto.trilha.TrilhaResumoResponse;
import com.nexo.entity.Area;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.exception.NomeDuplicadoException;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.CriterioRecomendacao;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaFiltro;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.TrilhaSpecifications;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.recomendacao.EstrategiaRecomendacao;
import com.nexo.service.recomendacao.EstrategiaRecomendacaoFactory;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta e cadastro de trilhas (Cards 05 e 08).
 *
 * <p><strong>Isolamento por tenant:</strong> o escopo é sempre o tenant do
 * {@link UsuarioAutenticado} do contexto de segurança — nunca um parâmetro
 * vindo do cliente. Trilha inexistente ou de outro tenant sempre resulta em
 * {@link ResourceNotFoundException} (404), nunca 403: um usuário não pode
 * descobrir, pela resposta, que uma trilha existe em outra empresa.</p>
 */
@Service
public class TrilhaService {

    private final TrilhaRepository trilhaRepository;
    private final TrilhaAtribuicaoService trilhaAtribuicaoService;
    private final TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;
    private final AreaRepository areaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RegraDeAtraso regraDeAtraso;
    private final EstrategiaRecomendacaoFactory recomendacoes;

    public TrilhaService(TrilhaRepository trilhaRepository,
                         TrilhaAtribuicaoService trilhaAtribuicaoService,
                         TrilhaAtribuicaoRepository trilhaAtribuicaoRepository,
                         AreaRepository areaRepository,
                         UsuarioRepository usuarioRepository,
                         RegraDeAtraso regraDeAtraso,
                         EstrategiaRecomendacaoFactory recomendacoes) {
        this.trilhaRepository = trilhaRepository;
        this.trilhaAtribuicaoService = trilhaAtribuicaoService;
        this.trilhaAtribuicaoRepository = trilhaAtribuicaoRepository;
        this.areaRepository = areaRepository;
        this.usuarioRepository = usuarioRepository;
        this.regraDeAtraso = regraDeAtraso;
        this.recomendacoes = recomendacoes;
    }

    /**
     * Trilhas ativas do tenant do requisitante (Card 05), cada uma com o
     * progresso dele, aplicando os filtros de {@link TrilhaFiltro}. Inativas
     * ficam de fora: a listagem é o catálogo do que o colaborador pode fazer
     * agora.
     *
     * <p>Com {@code minhas=true}, a consulta parte das atribuições do usuário,
     * porque o prazo — usado em {@code sort=prazo} — só existe nelas. Com
     * {@code recomendar}, as trilhas recomendadas vêm primeiro e a ordenação
     * pedida vale entre elas.</p>
     */
    @Transactional(readOnly = true)
    public Page<TrilhaResumoResponse> listar(TrilhaFiltro filtro, Pageable pageable, UsuarioAutenticado requisitante) {
        TrilhaFiltro criterios = filtro == null ? TrilhaFiltro.vazio() : filtro;
        if (criterios.somenteMinhas()) {
            return listarMinhas(criterios, pageable, requisitante);
        }

        BiFunction<Root<Trilha>, CriteriaBuilder, Expression<Integer>> prioridade =
                prioridadeDaRecomendacao(criterios.recomendar(), requisitante);
        // Com prioridade, a ordenação inteira é montada na Specification; o
        // Pageable vai sem sort para o repositório não sobrescrevê-la.
        Pageable pagina = prioridade == null
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<Trilha> trilhas = trilhaRepository.findAll(
                TrilhaSpecifications.comFiltro(criterios, requisitante.getTenantId(), requisitante.getId(),
                        regraDeAtraso.hoje(), pageable.getSort(), prioridade),
                pagina);
        Map<UUID, ProgressoTrilhaResponse> progressos = trilhas.isEmpty()
                ? Map.of()
                : trilhaAtribuicaoService.progressosDoRequisitante(requisitante);
        return trilhas.map(trilha -> {
            ProgressoTrilhaResponse progresso = progressos.get(trilha.getId());
            return progresso == null
                    ? TrilhaResumoResponse.de(trilha)
                    : TrilhaResumoResponse.de(trilha, comStatusEfetivo(progresso));
        });
    }

    /** Trilhas ativas de uma área do tenant, com os mesmos filtros da listagem. */
    @Transactional(readOnly = true)
    public Page<TrilhaResumoResponse> listarPorArea(UUID areaId, TrilhaFiltro filtro, Pageable pageable,
                                                    UsuarioAutenticado requisitante) {
        areaRepository.findById(areaId)
                .filter(area -> area.getTenant().getId().equals(requisitante.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Área não encontrada: " + areaId));
        TrilhaFiltro criterios = filtro == null ? TrilhaFiltro.vazio() : filtro;
        return listar(criterios.comArea(areaId), pageable, requisitante);
    }

    /**
     * Trilha completa + progresso do requisitante nela (ou "não iniciado",
     * quando ele nunca acessou — ver {@link TrilhaAtribuicaoService#buscarProgressoOuNaoIniciado}).
     */
    @Transactional(readOnly = true)
    public TrilhaDetalheResponse buscarDetalhe(UUID trilhaId, UsuarioAutenticado requisitante) {
        Trilha trilha = buscarEntidadeNoTenant(trilhaId, requisitante.getTenantId());
        ProgressoTrilhaResponse progresso = trilhaAtribuicaoService.buscarProgressoOuNaoIniciado(trilha, requisitante);
        return TrilhaDetalheResponse.de(trilha, comStatusEfetivo(progresso));
    }

    private Page<TrilhaResumoResponse> listarMinhas(TrilhaFiltro filtro, Pageable pageable,
                                                    UsuarioAutenticado requisitante) {
        boolean porPrazo = pageable.getSort().getOrderFor("prazo") != null;
        Page<TrilhaAtribuicao> atribuicoes = trilhaAtribuicaoRepository.findAll(
                TrilhaSpecifications.minhas(filtro, requisitante.getTenantId(), requisitante.getId(),
                        regraDeAtraso.hoje(), porPrazo),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        return atribuicoes.map(atribuicao -> TrilhaResumoResponse.de(
                atribuicao.getTrilha(), comStatusEfetivo(ProgressoTrilhaResponse.de(atribuicao))));
    }

    private BiFunction<Root<Trilha>, CriteriaBuilder, Expression<Integer>> prioridadeDaRecomendacao(
            CriterioRecomendacao criterio, UsuarioAutenticado requisitante) {
        if (criterio == null) {
            return null;
        }
        EstrategiaRecomendacao estrategia = recomendacoes.para(criterio);
        EstrategiaRecomendacao.Contexto contexto = new EstrategiaRecomendacao.Contexto(areaDoUsuario(requisitante));
        return (trilha, cb) -> estrategia.prioridade(trilha, cb, contexto);
    }

    private UUID areaDoUsuario(UsuarioAutenticado requisitante) {
        return usuarioRepository.findById(requisitante.getId())
                .map(Usuario::getArea)
                .map(Area::getId)
                .orElse(null);
    }

    private ProgressoTrilhaResponse comStatusEfetivo(ProgressoTrilhaResponse progresso) {
        return progresso.comStatus(regraDeAtraso.statusEfetivo(progresso.status(), progresso.prazo()));
    }

    /**
     * Cadastra uma trilha no tenant do requisitante, que fica registrado como
     * autor. O nome é único por tenant (sem diferenciar maiúsculas); a área,
     * quando informada, precisa ser uma área ativa do mesmo tenant.
     */
    @Transactional
    public TrilhaResumoResponse criar(CriarTrilhaRequest request, UsuarioAutenticado requisitante) {
        String nome = request.nome().trim();
        if (trilhaRepository.existsByTenantIdAndNomeIgnoreCase(requisitante.getTenantId(), nome)) {
            throw new NomeDuplicadoException("uma trilha", nome);
        }

        Usuario autor = usuarioRepository.findById(requisitante.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuário autenticado não encontrado na base: " + requisitante.getEmail()));

        Trilha trilha = new Trilha();
        trilha.setTenant(autor.getTenant());
        trilha.setNome(nome);
        trilha.setArea(resolverArea(request.areaId(), requisitante.getTenantId()));
        trilha.setPrazoDias(request.prazoDias());
        trilha.setObjetivos(textoOuNulo(request.objetivos()));
        trilha.setDescricao(textoOuNulo(request.descricao()));
        trilha.setCriadoPor(autor);
        trilha.setAtivo(true);
        return TrilhaResumoResponse.de(trilhaRepository.saveAndFlush(trilha));
    }

    private Area resolverArea(UUID areaId, UUID tenantId) {
        if (areaId == null) {
            return null;
        }
        Area area = areaRepository.findById(areaId)
                .filter(a -> a.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Área não encontrada: " + areaId));
        if (!area.isAtivo()) {
            throw new OperacaoNaoPermitidaException("Área inativa não pode receber novas trilhas: " + area.getNome());
        }
        return area;
    }

    private Trilha buscarEntidadeNoTenant(UUID trilhaId, UUID tenantId) {
        Trilha trilha = trilhaRepository.findById(trilhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Trilha não encontrada: " + trilhaId));
        if (!trilha.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Trilha não encontrada: " + trilhaId);
        }
        return trilha;
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
