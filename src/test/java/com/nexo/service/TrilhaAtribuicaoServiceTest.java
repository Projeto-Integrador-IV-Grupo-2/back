package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.trilha.AtribuicaoEmLoteResponse;
import com.nexo.dto.trilha.AtribuirTrilhaRequest;
import com.nexo.dto.trilha.AtualizarProgressoRequest;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.event.AtribuicaoAtrasadaEvent;
import com.nexo.event.TrilhaAtribuidaEvent;
import com.nexo.event.TrilhaConcluidaEvent;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cobre as regras de negócio do Card 07, isoladas de banco/HTTP via mocks
 * (mesmo estilo de {@code UsuarioServiceTest}).
 */
@ExtendWith(MockitoExtension.class)
class TrilhaAtribuicaoServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 6, 15);

    @Mock
    private TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;

    @Mock
    private TrilhaRepository trilhaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private ApplicationEventPublisher publisher;

    private TrilhaAtribuicaoService trilhaAtribuicaoService;

    private Tenant tenant;
    private UsuarioAutenticado requisitante;
    private Trilha trilha;
    private UUID trilhaId;

    /**
     * A regra de atraso entra de verdade (com o relógio fixo), e não mockada:
     * ela é quem decide o status que sai nas respostas, então mocká-la
     * esvaziaria as asserções de status destes testes.
     */
    private final RegraDeAtraso regraDeAtraso = new RegraDeAtraso(
            Clock.fixed(ZonedDateTime.of(HOJE.atStartOfDay(), ZoneId.systemDefault()).toInstant(),
                    ZoneId.systemDefault()));

    @BeforeEach
    void setUp() {
        trilhaAtribuicaoService = new TrilhaAtribuicaoService(
                trilhaAtribuicaoRepository, trilhaRepository, usuarioRepository, areaRepository,
                regraDeAtraso, publisher);
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
        verify(publisher, never()).publishEvent(any(Object.class));
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
    void concluirPublicaTrilhaConcluidaEvent() {
        TrilhaAtribuicao existente = progressoExistente(StatusAtribuicao.EM_ANDAMENTO);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.of(existente));
        when(trilhaAtribuicaoRepository.saveAndFlush(any(TrilhaAtribuicao.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        trilhaAtribuicaoService.concluir(trilhaId, requisitante);

        ArgumentCaptor<TrilhaConcluidaEvent> evento = ArgumentCaptor.forClass(TrilhaConcluidaEvent.class);
        verify(publisher).publishEvent(evento.capture());
        assertThat(evento.getValue().tenantId()).isEqualTo(tenant.getId());
        assertThat(evento.getValue().trilhaId()).isEqualTo(trilhaId);
        assertThat(evento.getValue().usuarioId()).isEqualTo(requisitante.getId());
        assertThat(evento.getValue().concluidoEm()).isEqualTo(existente.getConcluidoEm());
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

    // ---- Card 42: atribuição em lote e marcação de atraso ----------------

    @Test
    void atribuiTrilhaAosUsuariosInformados() {
        trilha.setAtivo(true);
        Usuario destinatario = colaboradorAtivo();
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(usuarioRepository.findById(destinatario.getId())).thenReturn(Optional.of(destinatario));
        when(usuarioRepository.getReferenceById(requisitante.getId()))
                .thenReturn(usuarioComId(requisitante.getId()));
        when(trilhaAtribuicaoRepository.existsByTrilhaIdAndUsuarioId(trilhaId, destinatario.getId()))
                .thenReturn(false);

        AtribuicaoEmLoteResponse resposta = trilhaAtribuicaoService.atribuir(
                trilhaId, new AtribuirTrilhaRequest(List.of(destinatario.getId()), null, HOJE.plusDays(7)),
                requisitante);

        assertThat(resposta.atribuidos()).isEqualTo(1);
        assertThat(resposta.ignorados()).isZero();
        assertThat(resposta.usuarioIds()).containsExactly(destinatario.getId());
        verify(publisher).publishEvent(any(TrilhaAtribuidaEvent.class));
    }

    @Test
    void atribuirDeNovoNaoDuplicaEContaComoIgnorado() {
        trilha.setAtivo(true);
        Usuario jaAtribuido = colaboradorAtivo();
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(usuarioRepository.findById(jaAtribuido.getId())).thenReturn(Optional.of(jaAtribuido));
        when(usuarioRepository.getReferenceById(requisitante.getId()))
                .thenReturn(usuarioComId(requisitante.getId()));
        when(trilhaAtribuicaoRepository.existsByTrilhaIdAndUsuarioId(trilhaId, jaAtribuido.getId()))
                .thenReturn(true);

        AtribuicaoEmLoteResponse resposta = trilhaAtribuicaoService.atribuir(
                trilhaId, new AtribuirTrilhaRequest(List.of(jaAtribuido.getId()), null, null), requisitante);

        assertThat(resposta.atribuidos()).isZero();
        assertThat(resposta.ignorados()).isEqualTo(1);
        verify(trilhaAtribuicaoRepository, never()).save(any(TrilhaAtribuicao.class));
        // Ninguém novo recebeu a trilha: não há o que anunciar.
        verify(publisher, never()).publishEvent(any(TrilhaAtribuidaEvent.class));
    }

    @Test
    void atribuicaoPorAreaAlcancaOsColaboradoresAtivos() {
        trilha.setAtivo(true);
        UUID areaId = UUID.randomUUID();
        Usuario daArea = colaboradorAtivo();
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(areaDoTenant(areaId)));
        when(usuarioRepository.findByTenantIdAndAreaIdAndAtivoTrue(tenant.getId(), areaId))
                .thenReturn(List.of(daArea));
        when(usuarioRepository.getReferenceById(requisitante.getId()))
                .thenReturn(usuarioComId(requisitante.getId()));
        when(trilhaAtribuicaoRepository.existsByTrilhaIdAndUsuarioId(trilhaId, daArea.getId())).thenReturn(false);

        AtribuicaoEmLoteResponse resposta = trilhaAtribuicaoService.atribuir(
                trilhaId, new AtribuirTrilhaRequest(null, areaId, null), requisitante);

        assertThat(resposta.atribuidos()).isEqualTo(1);
    }

    @Test
    void trilhaInativaNaoRecebeAtribuicao() {
        trilha.setAtivo(false);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));

        assertThatThrownBy(() -> trilhaAtribuicaoService.atribuir(
                trilhaId, new AtribuirTrilhaRequest(List.of(UUID.randomUUID()), null, null), requisitante))
                .isInstanceOf(OperacaoNaoPermitidaException.class);
    }

    @Test
    void usuarioInativoNaoRecebeAtribuicao() {
        trilha.setAtivo(true);
        Usuario inativo = colaboradorAtivo();
        inativo.setAtivo(false);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(usuarioRepository.findById(inativo.getId())).thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> trilhaAtribuicaoService.atribuir(
                trilhaId, new AtribuirTrilhaRequest(List.of(inativo.getId()), null, null), requisitante))
                .isInstanceOf(OperacaoNaoPermitidaException.class);
    }

    @Test
    void rotinaMarcaVencidasComoAtrasadaEAnunciaCadaUma() {
        TrilhaAtribuicao vencida = progressoExistente(StatusAtribuicao.EM_ANDAMENTO);
        vencida.setPrazo(HOJE.minusDays(1));
        vencida.setTrilha(trilha);
        vencida.setTenant(tenant);
        vencida.setUsuario(usuarioComId(UUID.randomUUID()));
        when(trilhaAtribuicaoRepository.findByPrazoBeforeAndStatusNotIn(
                HOJE, List.of(StatusAtribuicao.CONCLUIDA, StatusAtribuicao.ATRASADA)))
                .thenReturn(List.of(vencida));

        int marcadas = trilhaAtribuicaoService.marcarAtrasadas();

        assertThat(marcadas).isEqualTo(1);
        assertThat(vencida.getStatus()).isEqualTo(StatusAtribuicao.ATRASADA);
        verify(publisher).publishEvent(any(AtribuicaoAtrasadaEvent.class));
    }

    @Test
    void leituraMostraAtrasoMesmoAntesDaRotinaRodar() {
        TrilhaAtribuicao vencida = progressoExistente(StatusAtribuicao.EM_ANDAMENTO);
        vencida.setPrazo(HOJE.minusDays(3));
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.of(vencida));

        ProgressoTrilhaResponse resposta = trilhaAtribuicaoService.buscarProgresso(trilhaId, requisitante);

        // O status gravado continua EM_ANDAMENTO: quem traduz é a regra de prazo.
        assertThat(vencida.getStatus()).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
        assertThat(resposta.status()).isEqualTo(StatusAtribuicao.ATRASADA);
    }

    @Test
    void concluirTrilhaAtrasadaEhPermitidoEDeixaDeSerAtrasada() {
        TrilhaAtribuicao atrasada = progressoExistente(StatusAtribuicao.ATRASADA);
        atrasada.setPrazo(HOJE.minusDays(5));
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.of(atrasada));
        when(trilhaAtribuicaoRepository.saveAndFlush(atrasada)).thenReturn(atrasada);

        ProgressoTrilhaResponse resposta = trilhaAtribuicaoService.concluir(trilhaId, requisitante);

        assertThat(resposta.status()).isEqualTo(StatusAtribuicao.CONCLUIDA);
        assertThat(resposta.concluidoEm()).isNotNull();
    }

    @Test
    void iniciarUmaAtribuicaoDoGestorColocaEmAndamento() {
        TrilhaAtribuicao doGestor = progressoExistente(StatusAtribuicao.NAO_INICIADA);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.of(doGestor));
        when(trilhaAtribuicaoRepository.saveAndFlush(doGestor)).thenReturn(doGestor);

        ProgressoTrilhaResponse resposta = trilhaAtribuicaoService.iniciar(trilhaId, requisitante);

        assertThat(resposta.status()).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
        assertThat(doGestor.getStatus()).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
    }

    @Test
    void iniciarNaoMexeEmProgressoQueJaComecou() {
        TrilhaAtribuicao concluida = progressoExistente(StatusAtribuicao.CONCLUIDA);
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId()))
                .thenReturn(Optional.of(concluida));

        ProgressoTrilhaResponse resposta = trilhaAtribuicaoService.iniciar(trilhaId, requisitante);

        assertThat(resposta.status()).isEqualTo(StatusAtribuicao.CONCLUIDA);
        verify(trilhaAtribuicaoRepository, never()).saveAndFlush(any(TrilhaAtribuicao.class));
    }

    @Test
    void areaInexistenteNaAtribuicaoResultaEmNotFound() {
        trilha.setAtivo(true);
        UUID areaId = UUID.randomUUID();
        when(trilhaRepository.findById(trilhaId)).thenReturn(Optional.of(trilha));
        when(areaRepository.findById(areaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trilhaAtribuicaoService.atribuir(
                trilhaId, new AtribuirTrilhaRequest(null, areaId, null), requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private com.nexo.entity.Area areaDoTenant(UUID id) {
        com.nexo.entity.Area area = new com.nexo.entity.Area();
        area.setTenant(tenant);
        area.setNome("Área " + id);
        ReflectionTestUtils.setField(area, "id", id);
        return area;
    }

    private Usuario colaboradorAtivo() {
        Usuario usuario = usuarioComId(UUID.randomUUID());
        usuario.setTenant(tenant);
        usuario.setEmail("colaborador-" + UUID.randomUUID() + "@nexo.com");
        usuario.setAtivo(true);
        return usuario;
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
