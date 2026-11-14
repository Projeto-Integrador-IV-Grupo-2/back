package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.trilha.CriarTrilhaRequest;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.dto.trilha.TrilhaDetalheResponse;
import com.nexo.dto.trilha.TrilhaResumoResponse;
import com.nexo.dto.trilha.AtualizarTrilhaRequest;
import com.nexo.event.TrilhaInativadaEvent;
import com.nexo.event.TrilhaReativadaEvent;
import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.exception.NomeDuplicadoException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.CriterioRecomendacao;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaFiltro;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.recomendacao.EstrategiaRecomendacaoFactory;
import com.nexo.service.recomendacao.RecomendacaoPorArea;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cobre a composição trilha + progresso do Card 08, isolada de banco/HTTP.
 * {@link TrilhaAtribuicaoService} é chamado de verdade (não mockado) só para
 * o método puro {@code buscarProgressoOuNaoIniciado}; seu repositório
 * (a única dependência que tocaria o banco) é que é mockado.
 */
@ExtendWith(MockitoExtension.class)
class TrilhaServiceTest {

    @Mock
    private TrilhaRepository trilhaRepository;

    @Mock
    private TrilhaAtribuicaoService trilhaAtribuicaoService;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;

    @Mock
    private ApplicationEventPublisher publisher;

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 5);

    private TrilhaService trilhaService;

    private Tenant tenant;
    private UsuarioAutenticado requisitante;
    private Trilha trilha;
    private UUID trilhaId;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        trilhaService = new TrilhaService(trilhaRepository, trilhaAtribuicaoService, trilhaAtribuicaoRepository,
                areaRepository, usuarioRepository, new RegraDeAtraso(relogio),
                new EstrategiaRecomendacaoFactory(List.of(new RecomendacaoPorArea())), publisher);

        tenant = tenantComId();
        requisitante = new UsuarioAutenticado(
                UUID.randomUUID(), tenant.getId(), "Maria Silva", "maria.silva@empresa.com",
                "hash-bcrypt", Perfil.COLABORADOR, true);

        trilhaId = UUID.randomUUID();
        trilha = new Trilha();
        trilha.setTenant(tenant);
        trilha.setNome("Onboarding");
        ReflectionTestUtils.setField(trilha, "id", trilhaId);
    }

    @Test
    void retornaTrilhaComProgressoExistente() {
        ProgressoTrilhaResponse progresso = new ProgressoTrilhaResponse(
                UUID.randomUUID(), new ProgressoTrilhaResponse.Recurso(trilhaId, "Onboarding"),
                StatusAtribuicao.EM_ANDAMENTO, 50, null, null, null);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoService.buscarProgressoOuNaoIniciado(trilha, requisitante)).thenReturn(progresso);

        TrilhaDetalheResponse response = trilhaService.buscarDetalhe(trilhaId, requisitante);

        assertThat(response.id()).isEqualTo(trilhaId);
        assertThat(response.nome()).isEqualTo("Onboarding");
        assertThat(response.progresso().status()).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
    }

    @Test
    void retornaProgressoNaoIniciadoQuandoAplicavel() {
        ProgressoTrilhaResponse naoIniciado = ProgressoTrilhaResponse.naoIniciado(trilha);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoService.buscarProgressoOuNaoIniciado(trilha, requisitante)).thenReturn(naoIniciado);

        TrilhaDetalheResponse response = trilhaService.buscarDetalhe(trilhaId, requisitante);

        assertThat(response.progresso().id()).isNull();
        assertThat(response.progresso().status()).isEqualTo(StatusAtribuicao.NAO_INICIADA);
        assertThat(response.progresso().percentual()).isZero();
    }

    @Test
    void trilhaInexistenteLancaResourceNotFound() {
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trilhaService.buscarDetalhe(trilhaId, requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void trilhaDeOutroTenantLancaResourceNotFound() {
        trilha.setTenant(tenantComId());
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));

        assertThatThrownBy(() -> trilhaService.buscarDetalhe(trilhaId, requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listagemJuntaOProgressoDoRequisitanteEmCadaTrilha() {
        Trilha outra = new Trilha();
        outra.setTenant(tenant);
        outra.setNome("LGPD");
        ReflectionTestUtils.setField(outra, "id", UUID.randomUUID());
        Pageable pageable = PageRequest.of(0, 20);
        ProgressoTrilhaResponse emAndamento = new ProgressoTrilhaResponse(
                UUID.randomUUID(), new ProgressoTrilhaResponse.Recurso(trilhaId, "Onboarding"),
                StatusAtribuicao.EM_ANDAMENTO, 50, null, null, null);
        when(trilhaRepository.findAll(anyTrilhaSpec(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(trilha, outra)));
        when(trilhaAtribuicaoService.progressosDoRequisitante(requisitante))
                .thenReturn(Map.of(trilhaId, emAndamento));

        Page<TrilhaResumoResponse> pagina = trilhaService.listar(TrilhaFiltro.vazio(), pageable, requisitante);

        assertThat(pagina.getContent()).extracting(r -> r.progresso().status())
                .containsExactly(StatusAtribuicao.EM_ANDAMENTO, StatusAtribuicao.NAO_INICIADA);
    }

    @Test
    void listagemMostraComoAtrasadaATrilhaComPrazoVencido() {
        Pageable pageable = PageRequest.of(0, 20);
        ProgressoTrilhaResponse vencida = new ProgressoTrilhaResponse(
                UUID.randomUUID(), new ProgressoTrilhaResponse.Recurso(trilhaId, "Onboarding"),
                StatusAtribuicao.EM_ANDAMENTO, 50, HOJE.minusDays(1), null, null);
        when(trilhaRepository.findAll(anyTrilhaSpec(), eq(pageable))).thenReturn(new PageImpl<>(List.of(trilha)));
        when(trilhaAtribuicaoService.progressosDoRequisitante(requisitante)).thenReturn(Map.of(trilhaId, vencida));

        Page<TrilhaResumoResponse> pagina = trilhaService.listar(TrilhaFiltro.vazio(), pageable, requisitante);

        assertThat(pagina.getContent().get(0).progresso().status()).isEqualTo(StatusAtribuicao.ATRASADA);
    }

    @Test
    void minhasTrilhasPartemDasAtribuicoesDoUsuario() {
        TrilhaAtribuicao atribuicao = new TrilhaAtribuicao();
        ReflectionTestUtils.setField(atribuicao, "id", UUID.randomUUID());
        atribuicao.setTrilha(trilha);
        atribuicao.setStatus(StatusAtribuicao.CONCLUIDA);
        atribuicao.setPrazo(HOJE.minusDays(10));
        when(trilhaAtribuicaoRepository.findAll(anyAtribuicaoSpec(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(atribuicao)));

        Page<TrilhaResumoResponse> pagina = trilhaService.listar(
                new TrilhaFiltro(null, null, null, true, null),
                PageRequest.of(0, 20, Sort.by("prazo")),
                requisitante);

        // Concluída com prazo vencido continua concluída, nunca atrasada.
        assertThat(pagina.getContent()).singleElement()
                .extracting(r -> r.progresso().status()).isEqualTo(StatusAtribuicao.CONCLUIDA);
        verify(trilhaRepository, never()).findAll(anyTrilhaSpec(), any(Pageable.class));
    }

    @Test
    void comRecomendacaoAOrdenacaoVaiNaSpecificationENaoNoPageable() {
        when(usuarioRepository.findById(requisitante.getId())).thenReturn(Optional.empty());
        when(trilhaRepository.findAll(anyTrilhaSpec(), any(Pageable.class))).thenReturn(Page.empty());

        trilhaService.listar(
                new TrilhaFiltro(null, null, null, null, CriterioRecomendacao.AREA),
                PageRequest.of(1, 10, Sort.by("nome")),
                requisitante);

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(trilhaRepository).findAll(anyTrilhaSpec(), pagina.capture());
        assertThat(pagina.getValue().getSort().isSorted()).isFalse();
        assertThat(pagina.getValue().getPageNumber()).isEqualTo(1);
    }

    @Test
    void trilhasDeAreaDeOutroTenantRetornam404() {
        Area deOutraEmpresa = new Area();
        deOutraEmpresa.setTenant(tenantComId());
        ReflectionTestUtils.setField(deOutraEmpresa, "id", UUID.randomUUID());
        when(areaRepository.findById(deOutraEmpresa.getId())).thenReturn(Optional.of(deOutraEmpresa));

        assertThatThrownBy(() -> trilhaService.listarPorArea(
                deOutraEmpresa.getId(), null, PageRequest.of(0, 20), requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @SuppressWarnings("unchecked")
    private static Specification<Trilha> anyTrilhaSpec() {
        return any(Specification.class);
    }

    @SuppressWarnings("unchecked")
    private static Specification<TrilhaAtribuicao> anyAtribuicaoSpec() {
        return any(Specification.class);
    }

    @Test
    void criaTrilhaNoTenantComAutorEAreaDoRequisitante() {
        Usuario autor = new Usuario();
        autor.setTenant(tenant);
        Area area = new Area();
        area.setTenant(tenant);
        area.setNome("Comercial");
        ReflectionTestUtils.setField(area, "id", UUID.randomUUID());
        when(trilhaRepository.existsByTenantIdAndNomeIgnoreCase(tenant.getId(), "Vendas")).thenReturn(false);
        when(usuarioRepository.findById(requisitante.getId())).thenReturn(Optional.of(autor));
        when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));
        when(trilhaRepository.saveAndFlush(any(Trilha.class))).thenAnswer(inv -> {
            Trilha salva = inv.getArgument(0);
            ReflectionTestUtils.setField(salva, "id", UUID.randomUUID());
            return salva;
        });

        TrilhaResumoResponse criada = trilhaService.criar(
                new CriarTrilhaRequest("  Vendas ", area.getId(), 30, "  ", null), requisitante);

        ArgumentCaptor<Trilha> captor = ArgumentCaptor.forClass(Trilha.class);
        verify(trilhaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCriadoPor()).isSameAs(autor);
        assertThat(captor.getValue().getTenant()).isSameAs(tenant);
        assertThat(captor.getValue().getObjetivos()).isNull();
        assertThat(criada.nome()).isEqualTo("Vendas");
        assertThat(criada.area().nome()).isEqualTo("Comercial");
        assertThat(criada.progresso().status()).isEqualTo(StatusAtribuicao.NAO_INICIADA);
    }

    @Test
    void nomeDeTrilhaDuplicadoNoTenantLancaNomeDuplicado() {
        when(trilhaRepository.existsByTenantIdAndNomeIgnoreCase(tenant.getId(), "Onboarding")).thenReturn(true);

        assertThatThrownBy(() -> trilhaService.criar(
                new CriarTrilhaRequest("Onboarding", null, null, null, null), requisitante))
                .isInstanceOf(NomeDuplicadoException.class);
    }

    @Test
    void areaDeOutroTenantNaoPodeSerUsadaNaTrilha() {
        Area deOutraEmpresa = new Area();
        deOutraEmpresa.setTenant(tenantComId());
        ReflectionTestUtils.setField(deOutraEmpresa, "id", UUID.randomUUID());
        Usuario autor = new Usuario();
        autor.setTenant(tenant);
        when(usuarioRepository.findById(requisitante.getId())).thenReturn(Optional.of(autor));
        when(areaRepository.findById(deOutraEmpresa.getId())).thenReturn(Optional.of(deOutraEmpresa));

        assertThatThrownBy(() -> trilhaService.criar(
                new CriarTrilhaRequest("Nova", deOutraEmpresa.getId(), null, null, null), requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- Card 42: edição e situação da trilha ----------------------------

    @Test
    void editaTrilhaMantendoOProprioNome() {
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Onboarding"))
                .thenReturn(Optional.of(trilha));
        when(trilhaRepository.saveAndFlush(trilha)).thenReturn(trilha);

        TrilhaResumoResponse resposta = trilhaService.atualizar(
                trilhaId, new AtualizarTrilhaRequest("Onboarding", null, 10, "Novos objetivos", null), requisitante);

        assertThat(resposta.nome()).isEqualTo("Onboarding");
        assertThat(trilha.getPrazoDias()).isEqualTo(10);
        assertThat(trilha.getObjetivos()).isEqualTo("Novos objetivos");
    }

    @Test
    void recusaRenomearParaNomeDeOutraTrilha() {
        Trilha outra = new Trilha();
        ReflectionTestUtils.setField(outra, "id", UUID.randomUUID());
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Integração"))
                .thenReturn(Optional.of(outra));

        assertThatThrownBy(() -> trilhaService.atualizar(
                trilhaId, new AtualizarTrilhaRequest("Integração", null, null, null, null), requisitante))
                .isInstanceOf(NomeDuplicadoException.class);

        verify(trilhaRepository, never()).saveAndFlush(any(Trilha.class));
    }

    @Test
    void editarTrilhaDeOutroTenantResultaEmNotFound() {
        trilha.setTenant(tenantComId());
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));

        assertThatThrownBy(() -> trilhaService.atualizar(
                trilhaId, new AtualizarTrilhaRequest("Qualquer", null, null, null, null), requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inativarTiraDoCatalogoEAnuncia() {
        trilha.setAtivo(true);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaRepository.saveAndFlush(trilha)).thenReturn(trilha);

        trilhaService.inativar(trilhaId, requisitante);

        assertThat(trilha.isAtivo()).isFalse();
        ArgumentCaptor<TrilhaInativadaEvent> evento = ArgumentCaptor.forClass(TrilhaInativadaEvent.class);
        verify(publisher).publishEvent(evento.capture());
        assertThat(evento.getValue().trilhaId()).isEqualTo(trilhaId);
        assertThat(evento.getValue().inativadaPor()).isEqualTo(requisitante.getId());
    }

    @Test
    void ativarDevolveATrilhaAoCatalogo() {
        trilha.setAtivo(false);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaRepository.saveAndFlush(trilha)).thenReturn(trilha);

        trilhaService.ativar(trilhaId, requisitante);

        assertThat(trilha.isAtivo()).isTrue();
        verify(publisher, never()).publishEvent(any(TrilhaInativadaEvent.class));
        ArgumentCaptor<TrilhaReativadaEvent> evento = ArgumentCaptor.forClass(TrilhaReativadaEvent.class);
        verify(publisher).publishEvent(evento.capture());
        assertThat(evento.getValue().trilhaId()).isEqualTo(trilhaId);
        assertThat(evento.getValue().reativadaPor()).isEqualTo(requisitante.getId());
    }

    @Test
    void ativarUmaTrilhaJaAtivaNaoAnunciaNada() {
        trilha.setAtivo(true);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaRepository.saveAndFlush(trilha)).thenReturn(trilha);

        trilhaService.ativar(trilhaId, requisitante);

        verify(publisher, never()).publishEvent(any());
    }

    private Tenant tenantComId() {
        Tenant t = new Tenant();
        t.setNome("Empresa " + UUID.randomUUID());
        t.setSubdominio("emp-" + UUID.randomUUID());
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }
}
