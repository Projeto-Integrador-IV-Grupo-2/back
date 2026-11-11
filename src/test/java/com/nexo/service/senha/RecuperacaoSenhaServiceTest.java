package com.nexo.service.senha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.config.SenhaProperties;
import com.nexo.entity.PedidoRedefinicaoSenha.TipoChave;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.TokenRedefinicaoSenha;
import com.nexo.entity.Usuario;
import com.nexo.event.RedefinicaoSenhaSolicitadaEvent;
import com.nexo.event.SenhaRedefinidaEvent;
import com.nexo.exception.LimiteDePedidosExcedidoException;
import com.nexo.exception.TokenRedefinicaoInvalidoException;
import com.nexo.repository.PedidoRedefinicaoSenhaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.TokenRedefinicaoSenhaRepository;
import com.nexo.repository.UsuarioRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-05T15:00:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TokenRedefinicaoSenhaRepository tokenRepository;

    @Mock
    private PedidoRedefinicaoSenhaRepository pedidoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher publisher;

    private final GeradorDeToken gerador = new GeradorDeToken();
    private RecuperacaoSenhaService service;
    private Usuario ana;

    @BeforeEach
    void setUp() {
        service = new RecuperacaoSenhaService(usuarioRepository, tenantRepository, tokenRepository, pedidoRepository,
                gerador, passwordEncoder, publisher,
                new SenhaProperties(Duration.ofMinutes(30), 3, Duration.ofMinutes(15)),
                Clock.fixed(AGORA, ZoneOffset.UTC));
        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        tenant.setNome("Empresa Exemplo");
        ana = new Usuario();
        ana.setTenant(tenant);
        ana.setNomeCompleto("Ana Souza");
        ana.setEmail("ana@empresa.com");
        ana.setPapel(Perfil.COLABORADOR);
        ana.setAtivo(true);
        ReflectionTestUtils.setField(ana, "id", UUID.randomUUID());
    }

    @Test
    void solicitarGravaSoOHashComValidadeEAvisaParaMandarOEmail() {
        when(usuarioRepository.findByEmailIgnoreCaseAndAtivoTrue("ana@empresa.com")).thenReturn(List.of(ana));

        service.solicitar("  Ana@Empresa.com ", null, "10.0.0.1");

        ArgumentCaptor<TokenRedefinicaoSenha> token = ArgumentCaptor.forClass(TokenRedefinicaoSenha.class);
        verify(tokenRepository).save(token.capture());
        ArgumentCaptor<RedefinicaoSenhaSolicitadaEvent> evento =
                ArgumentCaptor.forClass(RedefinicaoSenhaSolicitadaEvent.class);
        verify(publisher).publishEvent(evento.capture());

        // O banco recebe o hash do mesmo token que vai no e-mail — nunca o valor.
        assertThat(token.getValue().getTokenHash()).isEqualTo(gerador.hash(evento.getValue().token()));
        assertThat(token.getValue().getTokenHash()).isNotEqualTo(evento.getValue().token());
        assertThat(token.getValue().getExpiraEm()).isEqualTo(OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC)
                .plusMinutes(30));
        assertThat(evento.getValue().email()).isEqualTo("ana@empresa.com");
        assertThat(evento.getValue().nomeEmpresa()).isEqualTo("Empresa Exemplo");
        // Pedir um link novo invalida os anteriores ainda não usados.
        verify(tokenRepository).apagarNaoUsadosDoUsuario(ana.getId());
    }

    @Test
    void emailSemContaNaoGeraLinkMasContaParaOLimiteIgual() {
        when(usuarioRepository.findByEmailIgnoreCaseAndAtivoTrue("ninguem@empresa.com")).thenReturn(List.of());

        service.solicitar("ninguem@empresa.com", null, "10.0.0.1");

        verify(tokenRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
        verify(pedidoRepository).save(org.mockito.ArgumentMatchers.argThat(p -> p.getTipoChave() == TipoChave.EMAIL));
        verify(pedidoRepository).save(org.mockito.ArgumentMatchers.argThat(p -> p.getTipoChave() == TipoChave.IP));
    }

    @Test
    void passarDoLimiteDePedidosResponde429SemGerarLink() {
        when(pedidoRepository.countByTipoChaveAndChaveHashAndCriadoEmAfter(eq(TipoChave.EMAIL), anyString(), any()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.solicitar("ana@empresa.com", null, "10.0.0.1"))
                .isInstanceOf(LimiteDePedidosExcedidoException.class)
                .extracting("segundosParaTentarDeNovo").isEqualTo(900L);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void limitePorIpTambemVale() {
        when(pedidoRepository.countByTipoChaveAndChaveHashAndCriadoEmAfter(eq(TipoChave.EMAIL), anyString(), any()))
                .thenReturn(0L);
        when(pedidoRepository.countByTipoChaveAndChaveHashAndCriadoEmAfter(eq(TipoChave.IP), anyString(), any()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.solicitar("outro@empresa.com", null, "10.0.0.1"))
                .isInstanceOf(LimiteDePedidosExcedidoException.class);
    }

    @Test
    void comEmpresaInformadaSoAContaAtivaDelaRecebe() {
        ana.setAtivo(false);
        when(tenantRepository.findBySubdominioIgnoreCase("nexo")).thenReturn(Optional.of(ana.getTenant()));
        when(usuarioRepository.findByTenantIdAndEmailIgnoreCase(ana.getTenant().getId(), "ana@empresa.com"))
                .thenReturn(Optional.of(ana));

        service.solicitar("ana@empresa.com", "Nexo", "10.0.0.1");

        // Inativo não recebe link.
        verify(tokenRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void redefinirTrocaASenhaMarcaOTokenComoUsadoEAvisa() {
        TokenRedefinicaoSenha token = tokenDaAna("valor", AGORA.plusSeconds(60));
        when(tokenRepository.findByTokenHash(gerador.hash("valor"))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NovaSenha1")).thenReturn("{bcrypt}hash");

        service.redefinir("valor", "NovaSenha1");

        assertThat(ana.getSenhaHash()).isEqualTo("{bcrypt}hash");
        assertThat(ana.getVersaoSenha()).isEqualTo(1);
        assertThat(token.getUsadoEm()).isEqualTo(OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC));
        verify(tokenRepository).apagarNaoUsadosDoUsuario(ana.getId());
        verify(publisher).publishEvent(new SenhaRedefinidaEvent(ana.getTenant().getId(), ana.getId()));
    }

    @Test
    void tokenExpiradoEhRecusado() {
        when(tokenRepository.findByTokenHash(gerador.hash("valor")))
                .thenReturn(Optional.of(tokenDaAna("valor", AGORA.minusSeconds(1))));

        assertThatThrownBy(() -> service.redefinir("valor", "NovaSenha1"))
                .isInstanceOf(TokenRedefinicaoInvalidoException.class);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void tokenJaUsadoEhRecusado() {
        TokenRedefinicaoSenha token = tokenDaAna("valor", AGORA.plusSeconds(60));
        token.marcarUsado(OffsetDateTime.ofInstant(AGORA.minusSeconds(10), ZoneOffset.UTC));
        when(tokenRepository.findByTokenHash(gerador.hash("valor"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.redefinir("valor", "NovaSenha1"))
                .isInstanceOf(TokenRedefinicaoInvalidoException.class);
    }

    @Test
    void tokenInventadoEhRecusado() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redefinir("inventado", "NovaSenha1"))
                .isInstanceOf(TokenRedefinicaoInvalidoException.class)
                .hasMessageContaining("Peça um novo link");
    }

    @Test
    void usuarioInativoNaoRedefine() {
        ana.setAtivo(false);
        when(tokenRepository.findByTokenHash(gerador.hash("valor")))
                .thenReturn(Optional.of(tokenDaAna("valor", AGORA.plusSeconds(60))));

        assertThatThrownBy(() -> service.redefinir("valor", "NovaSenha1"))
                .isInstanceOf(TokenRedefinicaoInvalidoException.class);
        verify(publisher, never()).publishEvent(any());
    }

    private TokenRedefinicaoSenha tokenDaAna(String valor, Instant expiraEm) {
        return TokenRedefinicaoSenha.novo(ana, gerador.hash(valor), OffsetDateTime.ofInstant(expiraEm, ZoneOffset.UTC));
    }
}
