package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.dto.trilha.TrilhaDetalheResponse;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.TrilhaRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cobre a composição trilha + progresso do Card 08, isolada de banco/HTTP.
 * {@link TrilhaAtribuicaoService} é chamado de verdade (não mockado) só para
 * o método puro {@code buscarProgressoOuNaoIniciado}; seu repositório
 * (a única dependência que tocaria o banco) é que é mockado.
 */
@ExtendWith(MockitoExtension.class)
class TrilhaServiceTest {

    @Mock
    private TrilhaRepository trilhaRepository;

    @Mock
    private TrilhaAtribuicaoService trilhaAtribuicaoService;

    private TrilhaService trilhaService;

    private Tenant tenant;
    private UsuarioAutenticado requisitante;
    private Trilha trilha;
    private UUID trilhaId;

    @BeforeEach
    void setUp() {
        trilhaService = new TrilhaService(trilhaRepository, trilhaAtribuicaoService);

        tenant = tenantComId();
        requisitante = new UsuarioAutenticado(
                UUID.randomUUID(), tenant.getId(), "Maria Silva", "maria.silva@empresa.com",
                "hash-bcrypt", Perfil.COLABORADOR, true);

        trilhaId = UUID.randomUUID();
        trilha = new Trilha();
        trilha.setTenant(tenant);
        trilha.setNome("Onboarding");
        ReflectionTestUtils.setField(trilha, "id", trilhaId);
    }

    @Test
    void retornaTrilhaComProgressoExistente() {
        ProgressoTrilhaResponse progresso = new ProgressoTrilhaResponse(
                UUID.randomUUID(), new ProgressoTrilhaResponse.Recurso(trilhaId, "Onboarding"),
                StatusAtribuicao.EM_ANDAMENTO, 50, null, null, null);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoService.buscarProgressoOuNaoIniciado(trilha, requisitante)).thenReturn(progresso);

        TrilhaDetalheResponse response = trilhaService.buscarDetalhe(trilhaId, requisitante);

        assertThat(response.id()).isEqualTo(trilhaId);
        assertThat(response.nome()).isEqualTo("Onboarding");
        assertThat(response.progresso().status()).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
    }

    @Test
    void retornaProgressoNaoIniciadoQuandoAplicavel() {
        ProgressoTrilhaResponse naoIniciado = ProgressoTrilhaResponse.naoIniciado(trilha);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoService.buscarProgressoOuNaoIniciado(trilha, requisitante)).thenReturn(naoIniciado);

        TrilhaDetalheResponse response = trilhaService.buscarDetalhe(trilhaId, requisitante);

        assertThat(response.progresso().id()).isNull();
        assertThat(response.progresso().status()).isEqualTo(StatusAtribuicao.NAO_INICIADA);
        assertThat(response.progresso().percentual()).isZero();
    }

    @Test
    void trilhaInexistenteLancaResourceNotFound() {
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trilhaService.buscarDetalhe(trilhaId, requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void trilhaDeOutroTenantLancaResourceNotFound() {
        trilha.setTenant(tenantComId());
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));

        assertThatThrownBy(() -> trilhaService.buscarDetalhe(trilhaId, requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Tenant tenantComId() {
        Tenant t = new Tenant();
        t.setNome("Empresa " + UUID.randomUUID());
        t.setSubdominio("emp-" + UUID.randomUUID());
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }
}
