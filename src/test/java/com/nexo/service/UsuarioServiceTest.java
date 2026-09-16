package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.usuario.AtualizarUsuarioRequest;
import com.nexo.dto.usuario.CriarUsuarioRequest;
import com.nexo.dto.usuario.UsuarioResponse;
import com.nexo.entity.Cargo;
import com.nexo.entity.Perfil;
import com.nexo.entity.SituacaoUsuario;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.exception.EmailDuplicadoException;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.CargoRepository;
import com.nexo.repository.UsuarioFiltro;
import com.nexo.repository.UsuarioRepository;
import java.time.LocalDate;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cobre as regras de negócio do Card 08, isoladas de banco/HTTP via mocks
 * (o comportamento com banco real é coberto por
 * {@code UsuarioControllerTest} e {@code UsuarioRepositoryTest}).
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final String ADMIN_EMAIL = "admin@nexo.com";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CargoRepository cargoRepository;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private Tenant tenant;
    private Usuario admin;
    private CriarUsuarioRequest requestValido;

    @BeforeEach
    void setUp() {
        tenant = tenantComId();

        admin = new Usuario();
        admin.setTenant(tenant);
        admin.setNomeCompleto("Admin");
        admin.setEmail(ADMIN_EMAIL);
        admin.setSenhaHash("hash-admin");
        admin.setPapel(Perfil.ADMIN);

        requestValido = new CriarUsuarioRequest(
                "Maria Silva", "Maria.Silva@Empresa.com", "SenhaForte1",
                Perfil.COLABORADOR, null, null, LocalDate.now().minusDays(1));

        // Comum à maioria dos testes: resolver o tenant a partir do e-mail do requisitante.
        when(usuarioRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
    }

    @Test
    void cadastraUsuarioComSenhaHasheadaNoTenantDoRequisitante() {
        when(usuarioRepository.existsByTenantIdAndEmailIgnoreCase(tenant.getId(), "maria.silva@empresa.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("SenhaForte1")).thenReturn("hash-bcrypt");
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponse response = usuarioService.criar(requestValido, ADMIN_EMAIL);

        assertThat(response.email()).isEqualTo("maria.silva@empresa.com");
        verify(passwordEncoder, times(1)).encode("SenhaForte1");
    }

    @Test
    void rejeitaCadastroComEmailJaExistenteNoMesmoTenant() {
        when(usuarioRepository.existsByTenantIdAndEmailIgnoreCase(tenant.getId(), "maria.silva@empresa.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> usuarioService.criar(requestValido, ADMIN_EMAIL))
                .isInstanceOf(EmailDuplicadoException.class);

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejeitaCargoDeOutroTenantAoCadastrar() {
        Cargo cargoDeOutraEmpresa = new Cargo();
        cargoDeOutraEmpresa.setTenant(tenantComId());
        cargoDeOutraEmpresa.setNome("Gerente");
        UUID cargoId = UUID.randomUUID();

        when(usuarioRepository.existsByTenantIdAndEmailIgnoreCase(any(), any())).thenReturn(false);
        when(cargoRepository.findById(cargoId)).thenReturn(Optional.of(cargoDeOutraEmpresa));

        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "Maria Silva", "maria@empresa.com", "SenhaForte1",
                Perfil.COLABORADOR, cargoId, null, LocalDate.now());

        assertThatThrownBy(() -> usuarioService.criar(request, ADMIN_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listaUsuariosAplicandoFiltroEPaginacao() {
        Usuario usuario = usuarioAtivo();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Usuario> pagina = new PageImpl<>(List.of(usuario), pageable, 1);
        when(usuarioRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pagina);

        Page<UsuarioResponse> resultado = usuarioService.listar(UsuarioFiltro.vazio(), pageable, ADMIN_EMAIL);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).email()).isEqualTo(usuario.getEmail());
    }

    @Test
    void buscaPorIdInexistenteLancaResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.buscarPorId(id, ADMIN_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buscaPorIdDeOutroTenantTambemLancaResourceNotFound() {
        Usuario usuarioDeOutroTenant = usuarioAtivo();
        usuarioDeOutroTenant.setTenant(tenantComId());
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuarioDeOutroTenant));

        // Nunca 403: para o admin do tenant errado, o registro "não existe".
        assertThatThrownBy(() -> usuarioService.buscarPorId(id, ADMIN_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void editaNomeCargoAreaEPerfil() {
        Usuario usuario = usuarioAtivo();
        when(usuarioRepository.findById(any(UUID.class))).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AtualizarUsuarioRequest request = new AtualizarUsuarioRequest(
                "Maria S. Souza", null, null, Perfil.ADMIN);

        UsuarioResponse response = usuarioService.atualizar(UUID.randomUUID(), request, ADMIN_EMAIL);

        assertThat(response.nomeCompleto()).isEqualTo("Maria S. Souza");
        assertThat(response.papel()).isEqualTo(Perfil.ADMIN);
        // E-mail nunca muda por este fluxo.
        assertThat(response.email()).isEqualTo(usuario.getEmail());
    }

    @Test
    void desativaUsuarioSemExcluirRegistro() {
        Usuario usuario = usuarioAtivo();
        when(usuarioRepository.findById(any(UUID.class))).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponse response = usuarioService.atualizarSituacao(
                UUID.randomUUID(), SituacaoUsuario.INATIVO, ADMIN_EMAIL);

        assertThat(response.situacao()).isEqualTo(SituacaoUsuario.INATIVO);
        verify(usuarioRepository, never()).deleteById(any());
        verify(usuarioRepository, never()).delete((Usuario) any());    }

    @Test
    void reativaUsuarioDesativado() {
        Usuario usuario = usuarioAtivo();
        usuario.setSituacao(SituacaoUsuario.INATIVO);
        when(usuarioRepository.findById(any(UUID.class))).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponse response = usuarioService.atualizarSituacao(
                UUID.randomUUID(), SituacaoUsuario.ATIVO, ADMIN_EMAIL);

        assertThat(response.situacao()).isEqualTo(SituacaoUsuario.ATIVO);
    }

    @Test
    void impedeAdministradorDeDesativarAPropriaConta() {
        when(usuarioRepository.findById(any(UUID.class))).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> usuarioService.atualizarSituacao(
                UUID.randomUUID(), SituacaoUsuario.INATIVO, ADMIN_EMAIL))
                .isInstanceOf(OperacaoNaoPermitidaException.class);

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void permiteAdministradorDesativarOutraConta() {
        Usuario outroUsuario = usuarioAtivo();
        when(usuarioRepository.findById(any(UUID.class))).thenReturn(Optional.of(outroUsuario));
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponse response = usuarioService.atualizarSituacao(
                UUID.randomUUID(), SituacaoUsuario.INATIVO, ADMIN_EMAIL);

        assertThat(response.situacao()).isEqualTo(SituacaoUsuario.INATIVO);
    }

    /** Tenant "persistido" com id preenchido via reflection — não há setId() público (só @GeneratedValue). */
    private Tenant tenantComId() {
        Tenant t = new Tenant();
        t.setNome("Empresa " + UUID.randomUUID());
        t.setSubdominio("emp-" + UUID.randomUUID());
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }

    private Usuario usuarioAtivo() {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Maria Silva");
        usuario.setEmail("maria.silva@empresa.com");
        usuario.setSenhaHash("hash-bcrypt");
        usuario.setPapel(Perfil.COLABORADOR);
        usuario.setDataEntrada(LocalDate.now().minusYears(1));
        return usuario;
    }
}
