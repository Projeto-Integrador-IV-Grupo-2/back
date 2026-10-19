package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.DashboardGestaoResponse;
import com.nexo.entity.Perfil;
import com.nexo.repository.AtribuicaoDashboardProjection;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.repository.HistoricoInteracaoRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardGestaoServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 15);

    @Mock
    private TrilhaRepository trilhaRepository;
    @Mock
    private TrilhaAtribuicaoRepository atribuicaoRepository;
    @Mock
    private HistoricoInteracaoRepository historicoRepository;

    private DashboardGestaoService service;
    private UUID tenantId;
    private UsuarioAutenticado requisitante;

    @BeforeEach
    void setUp() {
        service = new DashboardGestaoService(trilhaRepository, atribuicaoRepository, historicoRepository,
                new RegraDeAtraso(Clock.fixed(HOJE.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(),
                        ZoneId.of("America/Sao_Paulo"))));
        tenantId = UUID.randomUUID();
        requisitante = new UsuarioAutenticado(UUID.randomUUID(), tenantId, "Gestor", "g@empresa.com",
                "hash", Perfil.GESTOR, true);
    }

    @Test
    void montaIndicadoresSemExporDadosDeOutroTenant() {
        when(trilhaRepository.countByTenantIdAndAtivoTrue(tenantId)).thenReturn(3L);
        when(atribuicaoRepository.buscarDadosDashboardPorTenant(tenantId, PageRequest.of(0, 500)))
                .thenReturn(new SliceImpl<>(List.of(
                        atribuicao(StatusAtribuicao.NAO_INICIADA, null, null, null),
                        atribuicao(StatusAtribuicao.EM_ANDAMENTO, null, null, null),
                        atribuicao(StatusAtribuicao.CONCLUIDA, null, null, null),
                        atribuicao(StatusAtribuicao.CONCLUIDA, null, null, null)),
                        PageRequest.of(0, 500), false));
        when(historicoRepository.encontrarTrilhasMaisIniciadas(tenantId, 5)).thenReturn(List.of());

        DashboardGestaoResponse resposta = service.gerar(requisitante);

        assertThat(resposta.trilhasAtivas()).isEqualTo(3L);
        assertThat(resposta.atribuicoesPorStatus().total()).isEqualTo(4L);
        assertThat(resposta.atribuicoesPorStatus().concluidas()).isEqualTo(2L);
        assertThat(resposta.taxaConclusao()).isEqualByComparingTo("50.00");
        assertThat(resposta.areasComMaisAtraso()).isEmpty();
        assertThat(resposta.trilhasMaisIniciadas()).isEmpty();
    }

    @Test
    void agregaTodosOsLotesSemMaterializarTodasAsAtribuicoes() {
        UUID areaId = UUID.randomUUID();
        when(trilhaRepository.countByTenantIdAndAtivoTrue(tenantId)).thenReturn(1L);
        var pagina0 = PageRequest.of(0, 500);
        var pagina1 = PageRequest.of(1, 500);
        when(atribuicaoRepository.buscarDadosDashboardPorTenant(tenantId, pagina0))
                .thenReturn(new SliceImpl<>(List.of(
                        atribuicao(StatusAtribuicao.EM_ANDAMENTO, HOJE.minusDays(1), areaId, "Operações")),
                        pagina0, true));
        when(atribuicaoRepository.buscarDadosDashboardPorTenant(tenantId, pagina1))
                .thenReturn(new SliceImpl<>(List.of(
                        atribuicao(StatusAtribuicao.CONCLUIDA, HOJE.minusDays(3), areaId, "Operações")),
                        pagina1, false));
        when(historicoRepository.encontrarTrilhasMaisIniciadas(tenantId, 5)).thenReturn(List.of());

        DashboardGestaoResponse resposta = service.gerar(requisitante);

        assertThat(resposta.atribuicoesPorStatus().total()).isEqualTo(2L);
        assertThat(resposta.atribuicoesPorStatus().atrasadas()).isEqualTo(1L);
        assertThat(resposta.atribuicoesPorStatus().concluidas()).isEqualTo(1L);
        assertThat(resposta.areasComMaisAtraso()).hasSize(1);
        assertThat(resposta.areasComMaisAtraso().get(0).areaId()).isEqualTo(areaId);
        verify(atribuicaoRepository).buscarDadosDashboardPorTenant(tenantId, pagina0);
        verify(atribuicaoRepository).buscarDadosDashboardPorTenant(tenantId, pagina1);
    }

    private AtribuicaoDashboardProjection atribuicao(
            StatusAtribuicao status, LocalDate prazo, UUID areaId, String areaNome) {
        AtribuicaoDashboardProjection projection = org.mockito.Mockito.mock(AtribuicaoDashboardProjection.class);
        when(projection.getStatus()).thenReturn(status);
        when(projection.getPrazo()).thenReturn(prazo);
        when(projection.getAreaId()).thenReturn(areaId);
        when(projection.getAreaNome()).thenReturn(areaNome);
        return projection;
    }
}
