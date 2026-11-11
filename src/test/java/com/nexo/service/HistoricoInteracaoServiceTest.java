package com.nexo.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.entity.Perfil;
import com.nexo.entity.TipoInteracaoHistorico;
import com.nexo.exception.ParametroInvalidoException;
import com.nexo.repository.HistoricoInteracaoRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class HistoricoInteracaoServiceTest {

    @Mock
    private HistoricoInteracaoRepository repository;

    @Mock
    private HistoricoInteracaoFactory factory;

    private HistoricoInteracaoService service;

    @BeforeEach
    void setUp() {
        service = new HistoricoInteracaoService(repository, factory);
    }

    @Test
    void adminConsultaHistoricoCompletoDoProprioTenant() {
        UUID tenantId = UUID.randomUUID();
        UsuarioAutenticado admin = usuario(UUID.randomUUID(), tenantId, Perfil.ADMIN);
        when(repository.findAllByTenantId(eq(tenantId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listar(null, admin);

        verify(repository).findAllByTenantId(eq(tenantId), any(Pageable.class));
    }

    @Test
    void colaboradorConsultaSomenteHistoricoProprioDentroDoTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UsuarioAutenticado colaborador = usuario(usuarioId, tenantId, Perfil.COLABORADOR);
        when(repository.findAllByTenantIdAndUsuarioId(eq(tenantId), eq(usuarioId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listar(null, colaborador);

        verify(repository).findAllByTenantIdAndUsuarioId(eq(tenantId), eq(usuarioId), any(Pageable.class));
    }

    @Test
    void ultimasAtividadesFiltraTiposDeTrilhaAntesDeAplicarOLimite() {
        UUID tenantId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UsuarioAutenticado colaborador = usuario(usuarioId, tenantId, Perfil.COLABORADOR);
        List<TipoInteracaoHistorico> tiposDeTrilha = List.of(
                TipoInteracaoHistorico.TRILHA_ACESSADA,
                TipoInteracaoHistorico.TRILHA_INICIADA,
                TipoInteracaoHistorico.TRILHA_CONCLUIDA);
        when(repository.findTop10ByTenantIdAndUsuarioIdAndTipoInOrderByCriadoEmDescIdDesc(
                tenantId, usuarioId, tiposDeTrilha)).thenReturn(List.of());

        service.ultimasAtividades(colaborador);

        verify(repository).findTop10ByTenantIdAndUsuarioIdAndTipoInOrderByCriadoEmDescIdDesc(
                tenantId, usuarioId, tiposDeTrilha);
    }

    @Test
    void rejeitaOffsetDePaginacaoExcessivoAntesDeConsultarBanco() {
        UsuarioAutenticado colaborador = usuario(UUID.randomUUID(), UUID.randomUUID(), Perfil.COLABORADOR);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.listar(org.springframework.data.domain.PageRequest.of(1001, 100), colaborador))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("muito distante");

        verify(repository, never()).findAllByTenantIdAndUsuarioId(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    private UsuarioAutenticado usuario(UUID id, UUID tenantId, Perfil perfil) {
        return new UsuarioAutenticado(id, tenantId, "Teste", "teste@empresa.com", "hash", perfil, true);
    }
}
