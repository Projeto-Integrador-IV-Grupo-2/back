package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.entity.Conteudo;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusConteudo;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoConteudo;
import com.nexo.event.ConteudoPublicadoEvent;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.ConteudoRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConteudoServiceTest {

    @Mock
    private ConteudoRepository conteudoRepository;

    @Mock
    private ConteudoFactory conteudoFactory;

    @Mock
    private CategoriaService categoriaService;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ApplicationEventPublisher publisher;

    private ConteudoService service;
    private Tenant tenant;
    private UsuarioAutenticado admin;
    private UsuarioAutenticado colaborador;

    @BeforeEach
    void prepara() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        service = new ConteudoService(conteudoRepository, conteudoFactory, categoriaService, areaRepository,
                tenantRepository, usuarioRepository, publisher, clock);
        tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        admin = usuario(Perfil.ADMIN, tenant.getId());
        colaborador = usuario(Perfil.COLABORADOR, tenant.getId());
    }

    @Test
    void colaboradorNaoVeRascunhoPorId() {
        Conteudo rascunho = conteudo(StatusConteudo.RASCUNHO, tenant);
        when(conteudoRepository.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));

        assertThatThrownBy(() -> service.buscarPorId(rascunho.getId(), colaborador))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.buscarPorId(rascunho.getId(), admin).status()).isEqualTo(StatusConteudo.RASCUNHO);
    }

    @Test
    void conteudoDeOutroTenantRetorna404() {
        Tenant outro = new Tenant();
        ReflectionTestUtils.setField(outro, "id", UUID.randomUUID());
        Conteudo deFora = conteudo(StatusConteudo.PUBLICADO, outro);
        when(conteudoRepository.findById(deFora.getId())).thenReturn(Optional.of(deFora));

        assertThatThrownBy(() -> service.buscarPorId(deFora.getId(), admin))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void publicarMarcaDataEAvisaOsInteressados() {
        Conteudo rascunho = conteudo(StatusConteudo.RASCUNHO, tenant);
        when(conteudoRepository.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));
        when(conteudoRepository.saveAndFlush(rascunho)).thenReturn(rascunho);

        var resposta = service.publicar(rascunho.getId(), admin);

        assertThat(resposta.status()).isEqualTo(StatusConteudo.PUBLICADO);
        assertThat(resposta.publicadoEm()).isNotNull();
        verify(publisher).publishEvent(eq(new ConteudoPublicadoEvent(
                rascunho.getId(), tenant.getId(), null, admin.getId())));
    }

    @Test
    void publicarDeNovoNaoRepeteOEvento() {
        Conteudo publicado = conteudo(StatusConteudo.PUBLICADO, tenant);
        when(conteudoRepository.findById(publicado.getId())).thenReturn(Optional.of(publicado));

        service.publicar(publicado.getId(), admin);

        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void etapaNaoAceitaConteudoEmRascunho() {
        Conteudo rascunho = conteudo(StatusConteudo.RASCUNHO, tenant);
        when(conteudoRepository.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));

        assertThatThrownBy(() -> service.buscarPublicadoNoTenant(rascunho.getId(), tenant.getId()))
                .isInstanceOf(OperacaoNaoPermitidaException.class);
    }

    private static Conteudo conteudo(StatusConteudo status, Tenant dono) {
        Conteudo conteudo = new Conteudo();
        ReflectionTestUtils.setField(conteudo, "id", UUID.randomUUID());
        conteudo.setTenant(dono);
        conteudo.setTitulo("Conteúdo");
        conteudo.setTipo(TipoConteudo.TEXTO);
        conteudo.setCorpo("texto");
        conteudo.setStatus(status);
        return conteudo;
    }

    private static UsuarioAutenticado usuario(Perfil papel, UUID tenantId) {
        return new UsuarioAutenticado(UUID.randomUUID(), tenantId, "Pessoa", papel + "@nexo.com",
                "{bcrypt}x", papel, true);
    }
}
