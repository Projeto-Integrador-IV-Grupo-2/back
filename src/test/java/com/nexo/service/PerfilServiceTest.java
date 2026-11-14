package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.MeResponse;
import com.nexo.dto.perfil.AtualizarPerfilRequest;
import com.nexo.dto.perfil.TrocarSenhaRequest;
import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.event.SenhaTrocadaEvent;
import com.nexo.exception.CampoInvalidoException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    private static final String SENHA_ATUAL = "SenhaAtual1";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ApplicationEventPublisher publisher;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private PerfilService service;
    private Tenant tenant;
    private Usuario usuario;
    private UsuarioAutenticado autenticado;

    @BeforeEach
    void prepara() {
        service = new PerfilService(usuarioRepository, passwordEncoder, publisher);

        tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        tenant.setNome("Empresa Exemplo");

        usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Ana Souza");
        usuario.setEmail("ana@exemplo.com");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA_ATUAL));
        usuario.setPapel(Perfil.COLABORADOR);
        usuario.setAtivo(true);

        autenticado = UsuarioAutenticado.de(usuario);
    }

    @Test
    void dadosTrazemAreaEEmpresa() {
        Area area = new Area();
        ReflectionTestUtils.setField(area, "id", UUID.randomUUID());
        area.setNome("Suporte");
        usuario.setArea(area);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        MeResponse dados = service.dadosDe(usuario.getId(), tenant.getId());

        assertThat(dados.nomeCompleto()).isEqualTo("Ana Souza");
        assertThat(dados.area()).isEqualTo(new MeResponse.Recurso(area.getId(), "Suporte"));
        assertThat(dados.empresa()).isEqualTo(new MeResponse.Recurso(tenant.getId(), "Empresa Exemplo"));
    }

    @Test
    void usuarioSemAreaVemComAreaNula() {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertThat(service.dadosDe(usuario.getId(), tenant.getId()).area()).isNull();
    }

    @Test
    void usuarioDeOutroTenantNaoEEncontrado() {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.dadosDe(usuario.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void atualizarGravaONomeSemEspacosNasPontas() {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        MeResponse dados = service.atualizar(autenticado, new AtualizarPerfilRequest("  Ana Maria Souza  "));

        assertThat(usuario.getNomeCompleto()).isEqualTo("Ana Maria Souza");
        assertThat(dados.nomeCompleto()).isEqualTo("Ana Maria Souza");
    }

    @Test
    void trocarSenhaGravaONovoHash() {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        service.trocarSenha(autenticado, new TrocarSenhaRequest(SENHA_ATUAL, "NovaSenha2"));

        assertThat(passwordEncoder.matches("NovaSenha2", usuario.getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches(SENHA_ATUAL, usuario.getSenhaHash())).isFalse();
        assertThat(usuario.getVersaoSenha()).isEqualTo(1);
        verify(usuarioRepository).save(usuario);
        verify(publisher).publishEvent(new SenhaTrocadaEvent(autenticado.getTenantId(), autenticado.getId()));
    }

    @Test
    void senhaAtualErradaViraErroDeCampoEMantemASenha() {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        String hashAntes = usuario.getSenhaHash();

        assertThatThrownBy(() -> service.trocarSenha(autenticado, new TrocarSenhaRequest("Errada123", "NovaSenha2")))
                .isInstanceOf(CampoInvalidoException.class)
                .hasFieldOrPropertyWithValue("campo", "senhaAtual");
        assertThat(usuario.getSenhaHash()).isEqualTo(hashAntes);
        verify(usuarioRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void novaSenhaIgualAAtualERecusada() {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.trocarSenha(autenticado, new TrocarSenhaRequest(SENHA_ATUAL, SENHA_ATUAL)))
                .isInstanceOf(CampoInvalidoException.class)
                .hasFieldOrPropertyWithValue("campo", "novaSenha");
        verify(usuarioRepository, never()).save(any());
    }
}
