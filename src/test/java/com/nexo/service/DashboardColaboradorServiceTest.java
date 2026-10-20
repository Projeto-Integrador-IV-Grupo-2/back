package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nexo.dto.DashboardColaboradorResponse;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardColaboradorServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 15);

    @Mock
    private TrilhaAtribuicaoRepository atribuicaoRepository;

    @Mock
    private HistoricoInteracaoService historicoService;

    private DashboardColaboradorService service;
    private UsuarioAutenticado requisitante;
    private UUID tenantId;
    private UUID usuarioId;
    private TrilhaAtribuicao naoIniciada;
    private TrilhaAtribuicao emAndamento;
    private TrilhaAtribuicao concluida;

    @BeforeEach
    void setUp() {
        service = new DashboardColaboradorService(
                atribuicaoRepository,
                new RegraDeAtraso(Clock.fixed(HOJE.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(),
                        ZoneId.of("America/Sao_Paulo"))),
                historicoService);
        tenantId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
        requisitante = new UsuarioAutenticado(usuarioId, tenantId, "Colaborador", "c@empresa.com",
                "hash", Perfil.COLABORADOR, true);

        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", tenantId);
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", usuarioId);
        usuario.setTenant(tenant);

        naoIniciada = atribuicao(tenant, usuario, "Privacidade", HOJE.plusDays(7), StatusAtribuicao.NAO_INICIADA);
        emAndamento = atribuicao(tenant, usuario, "Onboarding", HOJE.plusDays(3), StatusAtribuicao.EM_ANDAMENTO);
        emAndamento.setPercentual(40);
        concluida = atribuicao(tenant, usuario, "Segurança", null, StatusAtribuicao.CONCLUIDA);
    }

    @Test
    void calculaResumoMediaRealEPrazosComEscopoDoUsuario() {
        when(atribuicaoRepository.findByTenantIdAndUsuarioId(tenantId, usuarioId))
                .thenReturn(List.of(naoIniciada, emAndamento, concluida));
        when(atribuicaoRepository.findByTenantIdAndUsuarioIdAndPrazoBetweenAndStatusNotOrderByPrazoAsc(
                tenantId, usuarioId, HOJE, HOJE.plusDays(7), StatusAtribuicao.CONCLUIDA))
                .thenReturn(List.of(emAndamento, naoIniciada));
        when(historicoService.ultimasAtividades(requisitante)).thenReturn(List.of());

        DashboardColaboradorResponse resposta = service.gerar(requisitante);

        assertThat(resposta.trilhasPorStatus().total()).isEqualTo(3);
        assertThat(resposta.trilhasPorStatus().naoIniciadas()).isEqualTo(1);
        assertThat(resposta.trilhasPorStatus().emAndamento()).isEqualTo(1);
        assertThat(resposta.trilhasPorStatus().concluidas()).isEqualTo(1);
        assertThat(resposta.percentualMedio()).isEqualByComparingTo("46.67");
        assertThat(resposta.proximosPrazos().get(0).percentual()).isEqualTo(40);
        assertThat(resposta.proximosPrazos()).hasSize(2);
        assertThat(resposta.proximosPrazos().get(0).prazo()).isEqualTo(HOJE.plusDays(3));
    }

    private TrilhaAtribuicao atribuicao(Tenant tenant, Usuario usuario, String nome, LocalDate prazo,
                                       StatusAtribuicao status) {
        Trilha trilha = new Trilha();
        trilha.setTenant(tenant);
        trilha.setNome(nome);
        ReflectionTestUtils.setField(trilha, "id", UUID.randomUUID());
        TrilhaAtribuicao atribuicao = TrilhaAtribuicao.nova(trilha, usuario, usuario, prazo, status);
        ReflectionTestUtils.setField(atribuicao, "id", UUID.randomUUID());
        return atribuicao;
    }
}
