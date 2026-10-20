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
import com.nexo.entity.Perfil;
import com.nexo.exception.NomeDuplicadoException;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.ConteudoRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.security.UsuarioAutenticado;
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

    @Mock
    private ConteudoRepository conteudoRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private AreaService areaService;

    private Tenant tenant;
    private UsuarioAutenticado requisitante;

    @BeforeEach
    void preparaRequisitante() {
        tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());

        UUID usuarioId = UUID.randomUUID();
        requisitante = new UsuarioAutenticado(usuarioId, tenant.getId(), "Admin", ADMIN_EMAIL,
                "hash", Perfil.ADMIN, true);
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
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

        AreaResponse resposta = areaService.criar(new AreaRequest("  Engenharia  ", null), requisitante);

        assertThat(resposta.nome()).isEqualTo("Engenharia");
        assertThat(resposta.ativo()).isTrue();
    }

    @Test
    void recusaNomeDuplicadoNoMesmoTenant() {
        when(areaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Engenharia"))
                .thenReturn(Optional.of(areaDoTenant("Engenharia")));

        assertThatThrownBy(() -> areaService.criar(new AreaRequest("Engenharia", null), requisitante))
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

        AreaResponse resposta = areaService.atualizar(area.getId(), new AreaRequest("Engenharia", null), requisitante);

        assertThat(resposta.nome()).isEqualTo("Engenharia");
    }

    @Test
    void recusaRenomearParaNomeDeOutraArea() {
        Area area = areaDoTenant("Engenharia");
        Area outra = areaDoTenant("Produto");
        when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));
        when(areaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Produto"))
                .thenReturn(Optional.of(outra));

        assertThatThrownBy(() -> areaService.atualizar(area.getId(), new AreaRequest("Produto", null), requisitante))
                .isInstanceOf(NomeDuplicadoException.class);
    }

    @Test
    void recusaInativarAreaComUsuariosVinculados() {
        Area area = areaDoTenant("Engenharia");
        when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));
        when(usuarioRepository.existsByAreaId(area.getId())).thenReturn(true);

        assertThatThrownBy(() -> areaService.inativar(area.getId(), requisitante))
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

        AreaResponse resposta = areaService.inativar(area.getId(), requisitante);

        assertThat(resposta.ativo()).isFalse();
    }

    @Test
    void reativaArea() {
        Area area = areaDoTenant("Engenharia");
        area.setAtivo(false);
        when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));
        when(areaRepository.saveAndFlush(area)).thenReturn(area);

        assertThat(areaService.ativar(area.getId(), requisitante).ativo()).isTrue();
    }

    @Test
    void areaInexistenteResultaEmNotFound() {
        UUID id = UUID.randomUUID();
        when(areaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> areaService.buscarPorId(id, requisitante))
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

        assertThatThrownBy(() -> areaService.buscarPorId(area.getId(), requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listagemPadraoTrazSomenteAtivasDoTenant() {
        Pageable pageable = PageRequest.of(0, 20);
        when(areaRepository.findByTenantIdAndAtivoTrueAndNomeContainingIgnoreCase(tenant.getId(), "", pageable))
                .thenReturn(new PageImpl<>(List.of(areaDoTenant("Engenharia"))));

        Page<AreaResponse> areas = areaService.listar(null, false, pageable, requisitante);

        assertThat(areas.getContent()).extracting(AreaResponse::nome).containsExactly("Engenharia");
        verify(areaRepository, never()).findByTenantIdAndNomeContainingIgnoreCase(any(), any(), any());
    }

    @Test
    void listagemComInativasUsaConsultaCompleta() {
        Pageable pageable = PageRequest.of(0, 20);
        Area inativa = areaDoTenant("Compras");
        inativa.setAtivo(false);
        when(areaRepository.findByTenantIdAndNomeContainingIgnoreCase(tenant.getId(), "", pageable))
                .thenReturn(new PageImpl<>(List.of(inativa, areaDoTenant("Engenharia"))));

        Page<AreaResponse> areas = areaService.listar(null, true, pageable, requisitante);

        assertThat(areas.getContent()).extracting(AreaResponse::nome).containsExactly("Compras", "Engenharia");
    }

    @Test
    void listagemFiltraPorNomeSemEspacosEPreencheTotalDePessoas() {
        Pageable pageable = PageRequest.of(0, 20);
        Area engenharia = areaDoTenant("Engenharia");
        when(areaRepository.findByTenantIdAndAtivoTrueAndNomeContainingIgnoreCase(tenant.getId(), "eng", pageable))
                .thenReturn(new PageImpl<>(List.of(engenharia)));
        UsuarioRepository.TotalPorArea total = new UsuarioRepository.TotalPorArea() {
            @Override
            public UUID getAreaId() {
                return engenharia.getId();
            }

            @Override
            public long getTotal() {
                return 3L;
            }
        };
        when(usuarioRepository.contarPorArea(List.of(engenharia.getId()))).thenReturn(List.of(total));

        Page<AreaResponse> areas = areaService.listar("  eng ", false, pageable, requisitante);

        assertThat(areas.getContent()).singleElement()
                .extracting(AreaResponse::totalPessoas).isEqualTo(3L);
    }

    @Test
    void descricaoEmBrancoEhGravadaComoNula() {
        when(areaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Engenharia")).thenReturn(Optional.empty());
        when(areaRepository.saveAndFlush(any(Area.class))).thenAnswer(inv -> inv.getArgument(0));

        AreaResponse resposta = areaService.criar(new AreaRequest("Engenharia", "   "), requisitante);

        assertThat(resposta.descricao()).isNull();
    }

    private Area areaDoTenant(String nome) {
        Area area = new Area();
        ReflectionTestUtils.setField(area, "id", UUID.randomUUID());
        area.setTenant(tenant);
        area.setNome(nome);
        return area;
    }
}
