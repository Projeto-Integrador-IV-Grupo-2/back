package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.DashboardColaboradorResponse;
import com.nexo.dto.DashboardGestaoResponse;
import com.nexo.entity.Perfil;
import com.nexo.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardFactoryTest {

    @Test
    void selecionaPainelPeloPerfil() {
        DashboardColaboradorService colaboradorService = mock(DashboardColaboradorService.class);
        DashboardGestaoService gestaoService = mock(DashboardGestaoService.class);
        DashboardFactory factory = new DashboardFactory(colaboradorService, gestaoService);
        UUID tenantId = UUID.randomUUID();
        UsuarioAutenticado colaborador = usuario(tenantId, Perfil.COLABORADOR);
        UsuarioAutenticado gestor = usuario(tenantId, Perfil.GESTOR);
        DashboardColaboradorResponse painelColaborador = new DashboardColaboradorResponse(
                null, List.of(), BigDecimal.ZERO, List.of());
        DashboardGestaoResponse painelGestao = new DashboardGestaoResponse(
                0, null, BigDecimal.ZERO, List.of(), List.of());
        when(colaboradorService.gerar(colaborador)).thenReturn(painelColaborador);
        when(gestaoService.gerar(gestor)).thenReturn(painelGestao);

        assertThat(factory.para(Perfil.COLABORADOR).gerar(colaborador)).isSameAs(painelColaborador);
        assertThat(factory.para(Perfil.GESTOR).gerar(gestor)).isSameAs(painelGestao);
        verify(colaboradorService).gerar(colaborador);
        verify(gestaoService).gerar(gestor);
    }

    private UsuarioAutenticado usuario(UUID tenantId, Perfil perfil) {
        return new UsuarioAutenticado(UUID.randomUUID(), tenantId, "Teste", "teste@empresa.com", "hash", perfil, true);
    }
}
