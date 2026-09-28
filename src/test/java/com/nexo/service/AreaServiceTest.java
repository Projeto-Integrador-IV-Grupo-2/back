package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.area.AreaRequest;
import com.nexo.dto.area.AreaResponse;
import com.nexo.entity.Area;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.exception.NomeDuplicadoException;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cobre as regras de negócio do Card 03, isoladas de banco e HTTP via mocks
 * (o comportamento com banco real fica em {@code AreaControllerTest}).
 */
@ExtendWith(MockitoExtension.class)
class AreaServiceTest {

    private static final String ADMIN_EMAIL = "admin@nexo.com";

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private AreaService areaService;

    private Tenant tenant;
    private Usuario requisitante;

    @BeforeEach
    void preparaRequisitante() {
        tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());

        requisitante = new Usuario();
        requisitante.setTenant(tenant);
        requisitante.setEmail(ADMIN_EMAIL);

        when(usuarioRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.of(requisitante));
    }

    @Test
    void criaAreaNoTenantDoRequisitante() {
        when(areaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Engenharia"))
                .thenReturn(Optional.empty());
        when(areaRepository.saveAndFlush(any(Area.class))).thenAnswer(invocacao -> {
            Area salva = invocacao.getArgument(0);
            ReflectionTestUtils.setField(salva, "id", UUID.randomUUID());
            return salva;
        });

        AreaResponse resposta = areaService.criar(new AreaRequest("  Engenharia  "), ADMIN_EMAIL);

        assertThat(resposta.nome()).isEqualTo("Engenharia");
        assertThat(resposta.ativo()).isTrue();
    }

    @Test
    void recusaNomeDuplicadoNoMesmoTenant() {
        when(areaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Engenharia"))
                .thenReturn(Optional.of(areaDoTenant("Engenharia")));

        assertThatThrownBy(() -> areaService.criar(new AreaRequest("Engenharia"), ADMIN_EMAIL))
                .isInstanceOf(NomeDuplicadoException.class);

        verify(areaRepository, never()).saveAndFlush(any(Area.class));
    }

    @Test
    void permiteSalvarAreaMantendoOProprioNome() {
        Area area = areaDoTenant("Engenharia");
        when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));
        when(areaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Engenharia"))
                .thenReturn(Optional.of(area));
        when(areaRepository.saveAndFlush(area)).thenReturn(area);

        AreaResponse resposta = areaService.atualizar(area.getId(), new AreaRequest("Engenharia"), ADMIN_EMAIL);

        assertThat(resposta.nome()).isEqualTo("Engenharia");
    }

    @Test
    void recusaRenomearParaNomeDeOutraArea() {
        Area area = areaDoTenant("Engenharia");
        Area outra = areaDoTenant("Produto");
        when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));
        when(areaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Produto"))
                .thenReturn(Optional.of(outra));

        assertThatThrownBy(() -> areaService.atualizar(area.getId(), new AreaRequest("Produto"), ADMIN_EMAIL))
                .isInstanceOf(NomeDuplicadoException.class);
    }

    @Test
    void recusaInativarAreaComUsuariosVinculados() {
        Area area = areaDoTenant("Engenharia");
        when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));
        when(usuarioRepository.existsByAreaId(area.getId())).thenReturn(true);

        assertThatThrownBy(() -> areaService.inativar(area.getId(), ADMIN_EMAIL))
                .isInstanceOf(OperacaoNaoPermitidaException.class)
                .hasMessageContaining("Engenharia");

        assertThat(area.isAtivo()).isTrue();
        verify(areaRepository, never()).saveAndFlush(any(Area.class));
    }

    @Test
    void inativaAreaSemUsuariosVinculados() {
        Area area = areaDoTenant("Engenharia");
        when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));
        when(usuarioRepository.existsByAreaId(area.getId())).thenReturn(false);
        when(areaRepository.saveAndFlush(area)).thenReturn(area);

        AreaResponse resposta = areaService.inativar(area.getId(), ADMIN_EMAIL);

        assertThat(resposta.ativo()).isFalse();
    }

    @Test
    void reativaArea() {
        Area area = areaDoTenant("Engenharia");
        area.setAtivo(false);
        when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));
        when(areaRepository.saveAndFlush(area)).thenReturn(area);

        assertThat(areaService.ativar(area.getId(), ADMIN_EMAIL).ativo()).isTrue();
    }

    @Test
    void areaInexistenteResultaEmNotFound() {
        UUID id = UUID.randomUUID();
        when(areaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> areaService.buscarPorId(id, ADMIN_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void areaDeOutroTenantResultaEmNotFoundEmVezDeAcessoNegado() {
        Tenant outroTenant = new Tenant();
        ReflectionTestUtils.setField(outroTenant, "id", UUID.randomUUID());
        Area area = new Area();
        ReflectionTestUtils.setField(area, "id", UUID.randomUUID());
        area.setTenant(outroTenant);
        area.setNome("Engenharia");
        when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));

        assertThatThrownBy(() -> areaService.buscarPorId(area.getId(), ADMIN_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listagemPadraoTrazSomenteAtivasDoTenant() {
        when(areaRepository.findByTenantIdAndAtivoTrueOrderByNome(tenant.getId()))
                .thenReturn(List.of(areaDoTenant("Engenharia")));

        List<AreaResponse> areas = areaService.listar(false, ADMIN_EMAIL);

        assertThat(areas).extracting(AreaResponse::nome).containsExactly("Engenharia");
        verify(areaRepository, never()).findByTenantIdOrderByNome(any());
    }

    @Test
    void listagemComInativasUsaConsultaCompleta() {
        Area inativa = areaDoTenant("Compras");
        inativa.setAtivo(false);
        when(areaRepository.findByTenantIdOrderByNome(tenant.getId()))
                .thenReturn(List.of(inativa, areaDoTenant("Engenharia")));

        List<AreaResponse> areas = areaService.listar(true, ADMIN_EMAIL);

        assertThat(areas).extracting(AreaResponse::nome).containsExactly("Compras", "Engenharia");
    }

    private Area areaDoTenant(String nome) {
        Area area = new Area();
        ReflectionTestUtils.setField(area, "id", UUID.randomUUID());
        area.setTenant(tenant);
        area.setNome(nome);
        return area;
    }
}
