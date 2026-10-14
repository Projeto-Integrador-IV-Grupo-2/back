package com.nexo.service;

import com.nexo.dto.pdi.AtualizarStatusPdiRequest;
import com.nexo.dto.pdi.ColaboradorOpcaoResponse;
import com.nexo.dto.pdi.CriarPdiRequest;
import com.nexo.dto.pdi.PdiAcaoResponse;
import com.nexo.dto.pdi.PdiDetalheResponse;
import com.nexo.dto.pdi.PdiItemRequest;
import com.nexo.dto.pdi.PdiItemResponse;
import com.nexo.dto.pdi.PdiResumoResponse;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.entity.Competencia;
import com.nexo.entity.Pdi;
import com.nexo.entity.PdiAcao;
import com.nexo.entity.PdiItem;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusPdi;
import com.nexo.entity.Usuario;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.PdiAcaoRepository;
import com.nexo.repository.PdiItemRepository;
import com.nexo.repository.PdiRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio do PDI e dos seus itens (competências).
 *
 * <p><strong>Quem vê o quê:</strong> ADMIN vê todos os PDIs do tenant; GESTOR
 * vê os PDIs em que é o gestor; qualquer um vê o próprio (como colaborador).
 * Gerenciar (mudar status, incluir itens e ações) é do ADMIN ou do gestor do
 * PDI. PDI fora desse alcance — de outro tenant ou de outra equipe — responde
 * 404, nunca 403, para não revelar que existe.</p>
 *
 * <p>Bean sem estado (Singleton do Spring): o requisitante vem sempre por
 * parâmetro, extraído do token pelo controller.</p>
 */
@Service
public class PdiService {

    private final PdiRepository pdiRepository;
    private final PdiItemRepository pdiItemRepository;
    private final PdiAcaoRepository pdiAcaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;
    private final CompetenciaService competenciaService;

    public PdiService(PdiRepository pdiRepository,
                      PdiItemRepository pdiItemRepository,
                      PdiAcaoRepository pdiAcaoRepository,
                      UsuarioRepository usuarioRepository,
                      TrilhaAtribuicaoRepository trilhaAtribuicaoRepository,
                      CompetenciaService competenciaService) {
        this.pdiRepository = pdiRepository;
        this.pdiItemRepository = pdiItemRepository;
        this.pdiAcaoRepository = pdiAcaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.trilhaAtribuicaoRepository = trilhaAtribuicaoRepository;
        this.competenciaService = competenciaService;
    }

    /** Cria o PDI do colaborador; quem cria vira o gestor e o PDI nasce ATIVO ({@link Pdi#novo}). */
    @Transactional
    public PdiDetalheResponse criar(CriarPdiRequest request, UsuarioAutenticado requisitante) {
        Usuario colaborador = usuarioRepository.findById(request.colaboradorId())
                .filter(usuario -> usuario.getTenant().getId().equals(requisitante.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Colaborador não encontrado: " + request.colaboradorId()));
        if (request.fim().isBefore(request.inicio())) {
            throw new OperacaoNaoPermitidaException("O fim do ciclo não pode ser antes do início");
        }
        String ciclo = request.ciclo().trim();
        if (pdiRepository.existsByTenantIdAndColaboradorIdAndCiclo(requisitante.getTenantId(),
                colaborador.getId(), ciclo)) {
            throw cicloDuplicado(colaborador, ciclo);
        }

        Usuario gestor = usuarioRepository.getReferenceById(requisitante.getId());
        Pdi pdi = Pdi.novo(colaborador, gestor, ciclo, request.inicio(), request.fim());
        try {
            pdi = pdiRepository.saveAndFlush(pdi);
        } catch (DataIntegrityViolationException ex) {
            // Corrida entre a checagem e a gravação: a UNIQUE uq_pdi_tenant_colaborador_ciclo garante.
            throw cicloDuplicado(colaborador, ciclo);
        }
        return PdiDetalheResponse.de(pdi, List.of());
    }

    /**
     * Usuários ativos do tenant que podem receber um PDI — a lista do campo
     * "Colaborador" ao criar. Existe porque {@code GET /api/usuarios} é só do
     * ADMIN, e o GESTOR também cria PDIs; devolve só id, nome e e-mail.
     */
    @Transactional(readOnly = true)
    public Page<ColaboradorOpcaoResponse> listarColaboradores(String nome, Pageable pageable,
                                                              UsuarioAutenticado requisitante) {
        String trecho = nome == null ? "" : nome.trim();
        return usuarioRepository
                .findByTenantIdAndAtivoTrueAndNomeCompletoContainingIgnoreCase(
                        requisitante.getTenantId(), trecho, pageable)
                .map(ColaboradorOpcaoResponse::de);
    }

    /** PDIs que o requisitante pode ver, com o percentual de cada um. */
    @Transactional(readOnly = true)
    public Page<PdiResumoResponse> listar(Pageable pageable, UsuarioAutenticado requisitante) {
        UUID tenantId = requisitante.getTenantId();
        Page<Pdi> pdis = switch (requisitante.getPapel()) {
            case ADMIN -> pdiRepository.findByTenantId(tenantId, pageable);
            case GESTOR -> pdiRepository.findByTenantIdAndGestorId(tenantId, requisitante.getId(), pageable);
            case COLABORADOR -> pdiRepository.findByTenantIdAndColaboradorId(tenantId, requisitante.getId(), pageable);
        };

        List<UUID> ids = pdis.getContent().stream().map(Pdi::getId).toList();
        Map<UUID, PdiAcaoRepository.TotaisPorPdi> totais = ids.isEmpty()
                ? Map.of()
                : pdiAcaoRepository.totaisPorPdi(ids).stream()
                        .collect(Collectors.toMap(PdiAcaoRepository.TotaisPorPdi::getPdiId, Function.identity()));
        return pdis.map(pdi -> {
            PdiAcaoRepository.TotaisPorPdi total = totais.get(pdi.getId());
            return total == null
                    ? PdiResumoResponse.de(pdi, 0, 0)
                    : PdiResumoResponse.de(pdi, total.getTotal(), total.getConcluidas());
        });
    }

    @Transactional(readOnly = true)
    public PdiDetalheResponse detalhar(UUID id, UsuarioAutenticado requisitante) {
        Pdi pdi = buscarNoTenant(id, requisitante);
        boolean visivel = requisitante.getPapel() == Perfil.ADMIN
                || pdi.getGestor().getId().equals(requisitante.getId())
                || pdi.getColaborador().getId().equals(requisitante.getId());
        if (!visivel) {
            throw naoEncontrado(id);
        }
        return montarDetalhe(pdi);
    }

    /** PDI ativo do requisitante (o mais recente, se houver mais de um ciclo ativo). */
    @Transactional(readOnly = true)
    public PdiDetalheResponse meu(UsuarioAutenticado requisitante) {
        return pdiRepository.findFirstByTenantIdAndColaboradorIdAndStatusOrderByInicioDescCriadoEmDesc(
                        requisitante.getTenantId(), requisitante.getId(), StatusPdi.ATIVO)
                .map(this::montarDetalhe)
                .orElseThrow(() -> new ResourceNotFoundException("Você não tem PDI ativo"));
    }

    @Transactional
    public PdiDetalheResponse mudarStatus(UUID id, AtualizarStatusPdiRequest request,
                                          UsuarioAutenticado requisitante) {
        Pdi pdi = buscarGerenciavel(id, requisitante);
        pdi.setStatus(request.status());
        return montarDetalhe(pdiRepository.saveAndFlush(pdi));
    }

    /** Inclui uma competência no PDI. Competência repetida no mesmo PDI é erro de negócio. */
    @Transactional
    public PdiItemResponse adicionarItem(UUID pdiId, PdiItemRequest request, UsuarioAutenticado requisitante) {
        Pdi pdi = buscarGerenciavel(pdiId, requisitante);
        Competencia competencia = competenciaService.buscarNoTenant(request.competenciaId(),
                requisitante.getTenantId());
        if (!competencia.isAtivo()) {
            throw new OperacaoNaoPermitidaException(
                    "Competência inativa não pode ser incluída no PDI: " + competencia.getNome());
        }
        if (pdiItemRepository.existsByPdiIdAndCompetenciaId(pdi.getId(), competencia.getId())) {
            throw competenciaRepetida(competencia);
        }

        PdiItem item = PdiItem.novo(pdi, competencia, request.nivelAtual(), request.nivelAlvo(),
                textoOuNulo(request.observacao()));
        try {
            return PdiItemResponse.de(pdiItemRepository.saveAndFlush(item), List.of());
        } catch (DataIntegrityViolationException ex) {
            throw competenciaRepetida(competencia);
        }
    }

    /**
     * PDI que o requisitante pode gerenciar: ADMIN, ou o gestor do PDI. Usado
     * também por {@code PdiAcaoService}.
     */
    @Transactional(readOnly = true)
    public Pdi buscarGerenciavel(UUID id, UsuarioAutenticado requisitante) {
        Pdi pdi = buscarNoTenant(id, requisitante);
        boolean gerenciavel = requisitante.getPapel() == Perfil.ADMIN
                || pdi.getGestor().getId().equals(requisitante.getId());
        if (!gerenciavel) {
            throw naoEncontrado(id);
        }
        return pdi;
    }

    private PdiDetalheResponse montarDetalhe(Pdi pdi) {
        List<PdiItem> itens = pdiItemRepository.findByPdiIdOrderByCompetenciaNomeAsc(pdi.getId());
        Map<UUID, List<PdiAcao>> acoesPorItem = pdiAcaoRepository.findByPdiId(pdi.getId()).stream()
                .collect(Collectors.groupingBy(acao -> acao.getPdiItem().getId()));
        // Progresso do colaborador em cada trilha, numa consulta só.
        Map<UUID, ProgressoTrilhaResponse> progressos = trilhaAtribuicaoRepository
                .findByTenantIdAndUsuarioId(pdi.getTenant().getId(), pdi.getColaborador().getId())
                .stream()
                .collect(Collectors.toMap(a -> a.getTrilha().getId(), ProgressoTrilhaResponse::de));

        List<PdiItemResponse> respostas = itens.stream()
                .map(item -> PdiItemResponse.de(item, acoesPorItem.getOrDefault(item.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(PdiAcao::getPrazo,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(acao -> PdiAcaoResponse.de(acao, acao.getTrilha() == null
                                ? null
                                : progressos.get(acao.getTrilha().getId())))
                        .toList()))
                .toList();
        return PdiDetalheResponse.de(pdi, respostas);
    }

    private Pdi buscarNoTenant(UUID id, UsuarioAutenticado requisitante) {
        return pdiRepository.findById(id)
                .filter(pdi -> pdi.getTenant().getId().equals(requisitante.getTenantId()))
                .orElseThrow(() -> naoEncontrado(id));
    }

    private static ResourceNotFoundException naoEncontrado(UUID id) {
        return new ResourceNotFoundException("PDI não encontrado: " + id);
    }

    private static OperacaoNaoPermitidaException cicloDuplicado(Usuario colaborador, String ciclo) {
        return new OperacaoNaoPermitidaException(
                colaborador.getNomeCompleto() + " já tem um PDI no ciclo " + ciclo);
    }

    private static OperacaoNaoPermitidaException competenciaRepetida(Competencia competencia) {
        return new OperacaoNaoPermitidaException(
                "A competência " + competencia.getNome() + " já está neste PDI");
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
