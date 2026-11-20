package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.pdi.CriarPdiRequest;
import com.nexo.dto.pdi.PdiDetalheResponse;
import com.nexo.dto.pdi.PdiItemRequest;
import com.nexo.dto.pdi.PdiResumoResponse;
import com.nexo.entity.Competencia;
import com.nexo.entity.Pdi;
import com.nexo.entity.PdiAcao;
import com.nexo.entity.PdiItem;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAcaoPdi;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.StatusPdi;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoAcaoPdi;
import com.nexo.entity.TipoCompetencia;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.PdiAcaoRepository;
import com.nexo.repository.PdiItemRepository;
import com.nexo.repository.PdiRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PdiServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 12, 31);

    @Mock
    private PdiRepository pdiRepository;

    @Mock
    private PdiItemRepository pdiItemRepository;

    @Mock
    private PdiAcaoRepository pdiAcaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;

    @Mock
    private CompetenciaService competenciaService;

    @InjectMocks
    private PdiService pdiService;

    private Tenant tenant;
    private Usuario gestor;
    private Usuario colaborador;

    @BeforeEach
    void setUp() {
        tenant = tenantComId();
        gestor = usuario(tenant, Perfil.GESTOR, "Gestora");
        colaborador = usuario(tenant, Perfil.COLABORADOR, "Colaborador");
    }

    @Test
    void pdiNasceAtivoComQuemCriouComoGestor() {
        when(usuarioRepository.findById(colaborador.getId())).thenReturn(Optional.of(colaborador));
        when(usuarioRepository.getReferenceById(gestor.getId())).thenReturn(gestor);
        when(pdiRepository.saveAndFlush(any(Pdi.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PdiDetalheResponse response = pdiService.criar(
                new CriarPdiRequest(colaborador.getId(), " 2026 ", INICIO, FIM), autenticado(gestor));

        assertThat(response.status()).isEqualTo(StatusPdi.ATIVO);
        assertThat(response.gestor().id()).isEqualTo(gestor.getId());
        assertThat(response.colaborador().id()).isEqualTo(colaborador.getId());
        assertThat(response.ciclo()).isEqualTo("2026");
        assertThat(response.percentual()).isZero();
    }

    @Test
    void doisPdisDoMesmoColaboradorNoMesmoCicloLancaErroDeNegocio() {
        when(usuarioRepository.findById(colaborador.getId())).thenReturn(Optional.of(colaborador));
        when(pdiRepository.existsByTenantIdAndColaboradorIdAndCiclo(tenant.getId(), colaborador.getId(), "2026"))
                .thenReturn(true);

        assertThatThrownBy(() -> pdiService.criar(
                new CriarPdiRequest(colaborador.getId(), "2026", INICIO, FIM), autenticado(gestor)))
                .isInstanceOf(OperacaoNaoPermitidaException.class)
                .hasMessageContaining("já tem um PDI no ciclo 2026");

        verify(pdiRepository, never()).saveAndFlush(any());
    }

    @Test
    void criarPdiParaColaboradorDeOutroTenantLancaResourceNotFound() {
        Usuario deOutro = usuario(tenantComId(), Perfil.COLABORADOR, "Outro");
        when(usuarioRepository.findById(deOutro.getId())).thenReturn(Optional.of(deOutro));

        assertThatThrownBy(() -> pdiService.criar(
                new CriarPdiRequest(deOutro.getId(), "2026", INICIO, FIM), autenticado(gestor)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fimAntesDoInicioLancaErroDeNegocio() {
        when(usuarioRepository.findById(colaborador.getId())).thenReturn(Optional.of(colaborador));

        assertThatThrownBy(() -> pdiService.criar(
                new CriarPdiRequest(colaborador.getId(), "2026", FIM, INICIO), autenticado(gestor)))
                .isInstanceOf(OperacaoNaoPermitidaException.class);
    }

    @Test
    void colaboradorListaSoOsProprios() {
        Pageable pagina = PageRequest.of(0, 20);
        when(pdiRepository.findByTenantIdAndColaboradorId(tenant.getId(), colaborador.getId(), pagina))
                .thenReturn(Page.empty());

        pdiService.listar(pagina, autenticado(colaborador));

        verify(pdiRepository, never()).findByTenantId(any(), any());
        verify(pdiRepository, never()).findByTenantIdAndGestorId(any(), any(), any());
    }

    @Test
    void gestorListaSoOsPdisEmQueEhGestorComPercentual() {
        Pageable pagina = PageRequest.of(0, 20);
        Pdi pdi = pdi();
        when(pdiRepository.findByTenantIdAndGestorId(tenant.getId(), gestor.getId(), pagina))
                .thenReturn(new PageImpl<>(List.of(pdi)));
        // 4 ações, 1 concluída: 25%, o mesmo cálculo do detalhe.
        PdiItem item = PdiItem.novo(pdi, competencia(true), 2, 4, null);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        PdiAcao concluida = PdiAcao.descritiva(item, TipoAcaoPdi.MENTORIA, FIM, "Mentoria");
        concluida.mudarStatus(StatusAcaoPdi.CONCLUIDA, OffsetDateTime.now());
        when(pdiItemRepository.findByPdiIdOrderByCompetenciaNomeAsc(pdi.getId())).thenReturn(List.of(item));
        when(pdiAcaoRepository.findByPdiId(pdi.getId())).thenReturn(List.of(
                concluida,
                PdiAcao.descritiva(item, TipoAcaoPdi.OUTRO, FIM, "Leitura 1"),
                PdiAcao.descritiva(item, TipoAcaoPdi.OUTRO, FIM, "Leitura 2"),
                PdiAcao.descritiva(item, TipoAcaoPdi.OUTRO, FIM, "Leitura 3")));
        when(trilhaAtribuicaoRepository.findByTenantIdAndUsuarioId(tenant.getId(), colaborador.getId()))
                .thenReturn(List.of());

        Page<PdiResumoResponse> resultado = pdiService.listar(pagina, autenticado(gestor));

        assertThat(resultado.getContent()).singleElement().satisfies(resumo -> {
            assertThat(resumo.percentual()).isEqualTo(25);
            assertThat(resumo.totalAcoes()).isEqualTo(4);
            assertThat(resumo.acoesConcluidas()).isEqualTo(1);
        });
        verify(pdiRepository, never()).findByTenantId(any(), any());
    }

    @Test
    void colaboradorNaoVePdiDeOutroColaborador() {
        Pdi pdi = pdi();
        Usuario outro = usuario(tenant, Perfil.COLABORADOR, "Outro colaborador");
        when(pdiRepository.findById(pdi.getId())).thenReturn(Optional.of(pdi));

        assertThatThrownBy(() -> pdiService.detalhar(pdi.getId(), autenticado(outro)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void gestorNaoVePdiDeOutroGestor() {
        Pdi pdi = pdi();
        Usuario outroGestor = usuario(tenant, Perfil.GESTOR, "Outro gestor");
        when(pdiRepository.findById(pdi.getId())).thenReturn(Optional.of(pdi));

        assertThatThrownBy(() -> pdiService.detalhar(pdi.getId(), autenticado(outroGestor)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void pdiDeOutroTenantLancaResourceNotFoundAteParaAdmin() {
        Pdi pdi = pdi();
        Usuario adminDeOutro = usuario(tenantComId(), Perfil.ADMIN, "Admin de outra empresa");
        when(pdiRepository.findById(pdi.getId())).thenReturn(Optional.of(pdi));

        assertThatThrownBy(() -> pdiService.detalhar(pdi.getId(), autenticado(adminDeOutro)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void detalheTrazItensAcoesEPercentual() {
        Pdi pdi = pdi();
        PdiItem item = PdiItem.novo(pdi, competencia(true), 2, 4, null);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        PdiAcao concluida = PdiAcao.descritiva(item, TipoAcaoPdi.MENTORIA, FIM, "Mentoria");
        concluida.mudarStatus(StatusAcaoPdi.CONCLUIDA, OffsetDateTime.now());
        PdiAcao pendente = PdiAcao.descritiva(item, TipoAcaoPdi.OUTRO, FIM, "Leitura");
        when(pdiRepository.findById(pdi.getId())).thenReturn(Optional.of(pdi));
        when(pdiItemRepository.findByPdiIdOrderByCompetenciaNomeAsc(pdi.getId())).thenReturn(List.of(item));
        when(pdiAcaoRepository.findByPdiId(pdi.getId())).thenReturn(List.of(concluida, pendente));
        when(trilhaAtribuicaoRepository.findByTenantIdAndUsuarioId(tenant.getId(), colaborador.getId()))
                .thenReturn(List.of());

        PdiDetalheResponse response = pdiService.detalhar(pdi.getId(), autenticado(colaborador));

        assertThat(response.itens()).singleElement()
                .satisfies(i -> assertThat(i.acoes()).hasSize(2));
        assertThat(response.percentual()).isEqualTo(50);
    }

    @Test
    void percentualAcompanhaOProgressoDoColaboradorNaTrilha() {
        Pdi pdi = pdi();
        PdiItem item = PdiItem.novo(pdi, competencia(true), 2, 4, null);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        Trilha trilha = new Trilha();
        trilha.setTenant(tenant);
        trilha.setNome("Spring Boot");
        ReflectionTestUtils.setField(trilha, "id", UUID.randomUUID());
        PdiAcao acao = PdiAcao.deTrilha(item, trilha, FIM, null);
        // O colaborador está no meio da trilha: 60% das etapas feitas.
        TrilhaAtribuicao atribuicao = TrilhaAtribuicao.nova(
                trilha, colaborador, gestor, FIM, StatusAtribuicao.EM_ANDAMENTO);
        atribuicao.setPercentual(60);
        when(pdiRepository.findById(pdi.getId())).thenReturn(Optional.of(pdi));
        when(pdiItemRepository.findByPdiIdOrderByCompetenciaNomeAsc(pdi.getId())).thenReturn(List.of(item));
        when(pdiAcaoRepository.findByPdiId(pdi.getId())).thenReturn(List.of(acao));
        when(trilhaAtribuicaoRepository.findByTenantIdAndUsuarioId(tenant.getId(), colaborador.getId()))
                .thenReturn(List.of(atribuicao));

        PdiDetalheResponse response = pdiService.detalhar(pdi.getId(), autenticado(colaborador));

        // Antes ficava em 0% até a trilha acabar; agora sobe com o progresso.
        assertThat(response.percentual()).isEqualTo(60);
        assertThat(response.acoesConcluidas()).isZero();
    }

    @Test
    void meuSemPdiAtivoLancaResourceNotFound() {
        when(pdiRepository.findFirstByTenantIdAndColaboradorIdAndStatusOrderByInicioDescCriadoEmDesc(
                tenant.getId(), colaborador.getId(), StatusPdi.ATIVO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdiService.meu(autenticado(colaborador)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void competenciaRepetidaNoPdiLancaErroDeNegocio() {
        Pdi pdi = pdi();
        Competencia competencia = competencia(true);
        when(pdiRepository.findById(pdi.getId())).thenReturn(Optional.of(pdi));
        when(competenciaService.buscarNoTenant(competencia.getId(), tenant.getId())).thenReturn(competencia);
        when(pdiItemRepository.existsByPdiIdAndCompetenciaId(pdi.getId(), competencia.getId())).thenReturn(true);

        assertThatThrownBy(() -> pdiService.adicionarItem(pdi.getId(),
                new PdiItemRequest(competencia.getId(), 2, 4, null), autenticado(gestor)))
                .isInstanceOf(OperacaoNaoPermitidaException.class)
                .hasMessageContaining("já está neste PDI");

        verify(pdiItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void competenciaInativaNaoEntraNoPdi() {
        Pdi pdi = pdi();
        Competencia competencia = competencia(false);
        when(pdiRepository.findById(pdi.getId())).thenReturn(Optional.of(pdi));
        when(competenciaService.buscarNoTenant(competencia.getId(), tenant.getId())).thenReturn(competencia);

        assertThatThrownBy(() -> pdiService.adicionarItem(pdi.getId(),
                new PdiItemRequest(competencia.getId(), 2, 4, null), autenticado(gestor)))
                .isInstanceOf(OperacaoNaoPermitidaException.class);
    }

    @Test
    void adicionaItemNoPdiDoGestor() {
        Pdi pdi = pdi();
        Competencia competencia = competencia(true);
        when(pdiRepository.findById(pdi.getId())).thenReturn(Optional.of(pdi));
        when(competenciaService.buscarNoTenant(competencia.getId(), tenant.getId())).thenReturn(competencia);
        when(pdiItemRepository.saveAndFlush(any(PdiItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var item = pdiService.adicionarItem(pdi.getId(),
                new PdiItemRequest(competencia.getId(), 2, 4, "  foco em APIs "), autenticado(gestor));

        assertThat(item.nivelAtual()).isEqualTo(2);
        assertThat(item.nivelAlvo()).isEqualTo(4);
        assertThat(item.observacao()).isEqualTo("foco em APIs");
        assertThat(item.acoes()).isEmpty();
    }

    @Test
    void gestorQueNaoEhGestorDoPdiNaoGerencia() {
        Pdi pdi = pdi();
        Usuario outroGestor = usuario(tenant, Perfil.GESTOR, "Outro gestor");
        when(pdiRepository.findById(pdi.getId())).thenReturn(Optional.of(pdi));

        assertThatThrownBy(() -> pdiService.buscarGerenciavel(pdi.getId(), autenticado(outroGestor)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(pdiRepository, never()).saveAndFlush(eq(pdi));
    }

    private Pdi pdi() {
        Pdi pdi = Pdi.novo(colaborador, gestor, "2026", INICIO, FIM);
        ReflectionTestUtils.setField(pdi, "id", UUID.randomUUID());
        return pdi;
    }

    private Competencia competencia(boolean ativa) {
        Competencia competencia = new Competencia();
        competencia.setTenant(tenant);
        competencia.setNome("Java");
        competencia.setTipo(TipoCompetencia.TECNICA);
        competencia.setAtivo(ativa);
        ReflectionTestUtils.setField(competencia, "id", UUID.randomUUID());
        return competencia;
    }

    private static PdiAcaoRepository.TotaisPorPdi totais(UUID pdiId, long total, long concluidas) {
        return new PdiAcaoRepository.TotaisPorPdi() {
            @Override
            public UUID getPdiId() {
                return pdiId;
            }

            @Override
            public Long getTotal() {
                return total;
            }

            @Override
            public Long getConcluidas() {
                return concluidas;
            }
        };
    }

    private static UsuarioAutenticado autenticado(Usuario usuario) {
        return UsuarioAutenticado.de(usuario);
    }

    private static Usuario usuario(Tenant tenant, Perfil papel, String nome) {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto(nome);
        usuario.setEmail(UUID.randomUUID() + "@empresa.com");
        usuario.setSenhaHash("hash");
        usuario.setPapel(papel);
        usuario.setAtivo(true);
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        return usuario;
    }

    private static Tenant tenantComId() {
        Tenant t = new Tenant();
        t.setNome("Empresa " + UUID.randomUUID());
        t.setSubdominio("emp-" + UUID.randomUUID());
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }
}
