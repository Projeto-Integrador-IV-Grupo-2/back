package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.trilha.CriarTrilhaRequest;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.dto.trilha.TrilhaDetalheResponse;
import com.nexo.dto.trilha.TrilhaResumoResponse;
import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.Usuario;
import com.nexo.exception.NomeDuplicadoException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    private TrilhaService trilhaService;

    private Tenant tenant;
    private UsuarioAutenticado requisitante;
    private Trilha trilha;
    private UUID trilhaId;

    @BeforeEach
    void setUp() {
        trilhaService = new TrilhaService(trilhaRepository, trilhaAtribuicaoService, areaRepository, usuarioRepository);

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
        when(trilhaRepository.findByTenantIdAndAtivoTrue(tenant.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(trilha, outra)));
        when(trilhaAtribuicaoService.progressosDoRequisitante(requisitante))
                .thenReturn(Map.of(trilhaId, emAndamento));

        Page<TrilhaResumoResponse> pagina = trilhaService.listar(pageable, requisitante);

        assertThat(pagina.getContent()).extracting(r -> r.progresso().status())
                .containsExactly(StatusAtribuicao.EM_ANDAMENTO, StatusAtribuicao.NAO_INICIADA);
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

    private Tenant tenantComId() {
        Tenant t = new Tenant();
        t.setNome("Empresa " + UUID.randomUUID());
        t.setSubdominio("emp-" + UUID.randomUUID());
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }
}
