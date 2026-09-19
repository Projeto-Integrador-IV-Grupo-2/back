package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.trilha.AtualizarProgressoRequest;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cobre as regras de negócio do Card 07, isoladas de banco/HTTP via mocks
 * (mesmo estilo de {@code UsuarioServiceTest}).
 */
@ExtendWith(MockitoExtension.class)
class TrilhaAtribuicaoServiceTest {

    @Mock
    private TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;

    @Mock
    private TrilhaRepository trilhaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private TrilhaAtribuicaoService trilhaAtribuicaoService;

    private Tenant tenant;
    private UsuarioAutenticado requisitante;
    private Trilha trilha;
    private UUID trilhaId;

    @BeforeEach
    void setUp() {
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
    void iniciarCriaProgressoNaPrimeiraInteracao() {
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.empty());
        when(usuarioRepository.getReferenceById(requisitante.getId())).thenReturn(usuarioComId(requisitante.getId()));
        when(trilhaAtribuicaoRepository.saveAndFlush(any(TrilhaAtribuicao.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProgressoTrilhaResponse response = trilhaAtribuicaoService.iniciar(trilhaId, requisitante);

        assertThat(response.status()).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
        assertThat(response.trilha().nome()).isEqualTo("Onboarding");
        verify(trilhaAtribuicaoRepository, never()).findAll();
    }

    @Test
    void iniciarNaoDuplicaProgressoJaExistente() {
        TrilhaAtribuicao existente = progressoExistente(StatusAtribuicao.EM_ANDAMENTO);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.of(existente));

        ProgressoTrilhaResponse response = trilhaAtribuicaoService.iniciar(trilhaId, requisitante);

        assertThat(response.status()).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
        verify(trilhaAtribuicaoRepository, never()).saveAndFlush(any());
        verify(usuarioRepository, never()).getReferenceById(any());
    }

    @Test
    void iniciarComTrilhaInexistenteLancaResourceNotFound() {
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trilhaAtribuicaoService.iniciar(trilhaId, requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void iniciarComTrilhaDeOutroTenantLancaResourceNotFound() {
        trilha.setTenant(tenantComId());
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));

        assertThatThrownBy(() -> trilhaAtribuicaoService.iniciar(trilhaId, requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void atualizaStatusDoProgressoExistente() {
        TrilhaAtribuicao existente = progressoExistente(StatusAtribuicao.EM_ANDAMENTO);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.of(existente));
        when(trilhaAtribuicaoRepository.saveAndFlush(any(TrilhaAtribuicao.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProgressoTrilhaResponse response = trilhaAtribuicaoService.atualizar(
                trilhaId, new AtualizarProgressoRequest(StatusAtribuicao.ATRASADA), requisitante);

        assertThat(response.status()).isEqualTo(StatusAtribuicao.ATRASADA);
        assertThat(response.concluidoEm()).isNull();
    }

    @Test
    void atualizarParaConcluidaPreencheDataDeConclusao() {
        TrilhaAtribuicao existente = progressoExistente(StatusAtribuicao.EM_ANDAMENTO);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.of(existente));
        when(trilhaAtribuicaoRepository.saveAndFlush(any(TrilhaAtribuicao.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProgressoTrilhaResponse response = trilhaAtribuicaoService.atualizar(
                trilhaId, new AtualizarProgressoRequest(StatusAtribuicao.CONCLUIDA), requisitante);

        assertThat(response.status()).isEqualTo(StatusAtribuicao.CONCLUIDA);
        assertThat(response.concluidoEm()).isNotNull();
    }

    @Test
    void atualizarParaNaoIniciadaLancaOperacaoNaoPermitida() {
        TrilhaAtribuicao existente = progressoExistente(StatusAtribuicao.EM_ANDAMENTO);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> trilhaAtribuicaoService.atualizar(
                trilhaId, new AtualizarProgressoRequest(StatusAtribuicao.NAO_INICIADA), requisitante))
                .isInstanceOf(OperacaoNaoPermitidaException.class);

        verify(trilhaAtribuicaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void atualizarSemProgressoExistenteLancaResourceNotFound() {
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> trilhaAtribuicaoService.atualizar(
                trilhaId, new AtualizarProgressoRequest(StatusAtribuicao.EM_ANDAMENTO), requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void concluiProgressoEmAndamento() {
        TrilhaAtribuicao existente = progressoExistente(StatusAtribuicao.EM_ANDAMENTO);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.of(existente));
        when(trilhaAtribuicaoRepository.saveAndFlush(any(TrilhaAtribuicao.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProgressoTrilhaResponse response = trilhaAtribuicaoService.concluir(trilhaId, requisitante);

        assertThat(response.status()).isEqualTo(StatusAtribuicao.CONCLUIDA);
        assertThat(response.percentual()).isEqualTo(100);
        assertThat(response.concluidoEm()).isNotNull();
    }

    @Test
    void concluirSemProgressoExistenteLancaResourceNotFound() {
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> trilhaAtribuicaoService.concluir(trilhaId, requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buscarProgressoRetornaProgressoDoRequisitante() {
        TrilhaAtribuicao existente = progressoExistente(StatusAtribuicao.EM_ANDAMENTO);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.of(existente));

        ProgressoTrilhaResponse response = trilhaAtribuicaoService.buscarProgresso(trilhaId, requisitante);

        assertThat(response.trilha().id()).isEqualTo(trilhaId);
        assertThat(response.status()).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
    }

    @Test
    void buscarProgressoInexistenteLancaResourceNotFound() {
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> trilhaAtribuicaoService.buscarProgresso(trilhaId, requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buscarProgressoComTrilhaInexistenteLancaResourceNotFound() {
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trilhaAtribuicaoService.buscarProgresso(trilhaId, requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private TrilhaAtribuicao progressoExistente(StatusAtribuicao status) {
        TrilhaAtribuicao progresso = new TrilhaAtribuicao();
        progresso.setTenant(tenant);
        progresso.setTrilha(trilha);
        progresso.setUsuario(usuarioComId(requisitante.getId()));
        progresso.setAtribuidoPor(usuarioComId(requisitante.getId()));
        progresso.setStatus(status);
        ReflectionTestUtils.setField(progresso, "id", UUID.randomUUID());
        return progresso;
    }

    private Usuario usuarioComId(UUID id) {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    private Tenant tenantComId() {
        Tenant t = new Tenant();
        t.setNome("Empresa " + UUID.randomUUID());
        t.setSubdominio("emp-" + UUID.randomUUID());
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }
}
