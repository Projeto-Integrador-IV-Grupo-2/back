package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.pdi.AtualizarStatusAcaoRequest;
import com.nexo.dto.pdi.PdiAcaoRequest;
import com.nexo.dto.pdi.PdiAcaoResponse;
import com.nexo.entity.Competencia;
import com.nexo.entity.Pdi;
import com.nexo.entity.PdiAcao;
import com.nexo.entity.PdiItem;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAcaoPdi;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoAcaoPdi;
import com.nexo.entity.TipoCompetencia;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.event.TrilhaAtribuidaEvent;
import com.nexo.event.TrilhaConcluidaEvent;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.PdiAcaoRepository;
import com.nexo.repository.PdiItemRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.pdi.AcaoDeTrilha;
import com.nexo.service.pdi.AcaoDescritiva;
import com.nexo.service.pdi.AcaoPdiFactory;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
 * Ações do PDI com a factory e os criadores reais (só os repositórios são
 * mocks): cobre a atribuição da trilha sem duplicar e o Observer da conclusão.
 */
@ExtendWith(MockitoExtension.class)
class PdiAcaoServiceTest {

    private static final LocalDate PRAZO = LocalDate.of(2026, 6, 30);

    @Mock
    private PdiService pdiService;

    @Mock
    private PdiItemRepository pdiItemRepository;

    @Mock
    private PdiAcaoRepository pdiAcaoRepository;

    @Mock
    private TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;

    @Mock
    private TrilhaRepository trilhaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ApplicationEventPublisher publisher;

    private PdiAcaoService pdiAcaoService;

    private Tenant tenant;
    private Usuario gestor;
    private Usuario colaborador;
    private Pdi pdi;
    private PdiItem item;
    private Trilha trilha;
    private UsuarioAutenticado requisitante;

    @BeforeEach
    void setUp() {
        AcaoPdiFactory factory = new AcaoPdiFactory(List.of(
                new AcaoDeTrilha(trilhaRepository, trilhaAtribuicaoRepository, usuarioRepository, publisher),
                new AcaoDescritiva()));
        pdiAcaoService = new PdiAcaoService(pdiService, pdiItemRepository, pdiAcaoRepository,
                trilhaAtribuicaoRepository, factory);

        tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        gestor = usuario(Perfil.GESTOR);
        colaborador = usuario(Perfil.COLABORADOR);
        requisitante = UsuarioAutenticado.de(gestor);

        pdi = Pdi.novo(colaborador, gestor, "2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        ReflectionTestUtils.setField(pdi, "id", UUID.randomUUID());
        Competencia competencia = new Competencia();
        competencia.setTenant(tenant);
        competencia.setNome("Java");
        competencia.setTipo(TipoCompetencia.TECNICA);
        item = PdiItem.novo(pdi, competencia, 2, 4, null);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());

        trilha = new Trilha();
        trilha.setTenant(tenant);
        trilha.setNome("Spring Boot");
        ReflectionTestUtils.setField(trilha, "id", UUID.randomUUID());
    }

    @Test
    void acaoDeTrilhaAtribuiATrilhaAoColaboradorComOPrazoDaAcao() {
        prepararItemGerenciavel();
        when(trilhaRepository.findById(trilha.getId())).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilha.getId(), colaborador.getId()))
                .thenReturn(Optional.empty());
        when(usuarioRepository.getReferenceById(gestor.getId())).thenReturn(gestor);
        when(trilhaAtribuicaoRepository.saveAndFlush(any(TrilhaAtribuicao.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(pdiAcaoRepository.saveAndFlush(any(PdiAcao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PdiAcaoResponse response = pdiAcaoService.adicionar(pdi.getId(), item.getId(),
                new PdiAcaoRequest(TipoAcaoPdi.TRILHA, trilha.getId(), PRAZO, null), requisitante);

        ArgumentCaptor<TrilhaAtribuicao> atribuicao = ArgumentCaptor.forClass(TrilhaAtribuicao.class);
        verify(trilhaAtribuicaoRepository).saveAndFlush(atribuicao.capture());
        assertThat(atribuicao.getValue().getUsuario()).isEqualTo(colaborador);
        assertThat(atribuicao.getValue().getAtribuidoPor()).isEqualTo(gestor);
        assertThat(atribuicao.getValue().getPrazo()).isEqualTo(PRAZO);
        assertThat(atribuicao.getValue().getStatus()).isEqualTo(StatusAtribuicao.NAO_INICIADA);
        assertThat(atribuicao.getValue().getTenant()).isEqualTo(tenant);
        verify(publisher).publishEvent(new TrilhaAtribuidaEvent(
                trilha.getId(), tenant.getId(), List.of(colaborador.getId()), gestor.getId()));

        assertThat(response.tipo()).isEqualTo(TipoAcaoPdi.TRILHA);
        assertThat(response.trilha().id()).isEqualTo(trilha.getId());
        assertThat(response.status()).isEqualTo(StatusAcaoPdi.PENDENTE);
        assertThat(response.prazo()).isEqualTo(PRAZO);
    }

    @Test
    void acaoDeTrilhaReaproveitaAtribuicaoExistenteSemDuplicar() {
        prepararItemGerenciavel();
        TrilhaAtribuicao existente = atribuicao(StatusAtribuicao.EM_ANDAMENTO);
        when(trilhaRepository.findById(trilha.getId())).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilha.getId(), colaborador.getId()))
                .thenReturn(Optional.of(existente));
        when(pdiAcaoRepository.saveAndFlush(any(PdiAcao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PdiAcaoResponse response = pdiAcaoService.adicionar(pdi.getId(), item.getId(),
                new PdiAcaoRequest(TipoAcaoPdi.TRILHA, trilha.getId(), PRAZO, null), requisitante);

        verify(trilhaAtribuicaoRepository, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any());
        assertThat(response.status()).isEqualTo(StatusAcaoPdi.PENDENTE);
        assertThat(response.progressoTrilha().status()).isEqualTo(StatusAtribuicao.EM_ANDAMENTO);
    }

    @Test
    void acaoDeTrilhaJaConcluidaNasceConcluida() {
        prepararItemGerenciavel();
        TrilhaAtribuicao concluida = atribuicao(StatusAtribuicao.CONCLUIDA);
        concluida.setConcluidoEm(OffsetDateTime.now().minusDays(3));
        when(trilhaRepository.findById(trilha.getId())).thenReturn(Optional.of(trilha));
        when(trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilha.getId(), colaborador.getId()))
                .thenReturn(Optional.of(concluida));
        when(pdiAcaoRepository.saveAndFlush(any(PdiAcao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PdiAcaoResponse response = pdiAcaoService.adicionar(pdi.getId(), item.getId(),
                new PdiAcaoRequest(TipoAcaoPdi.TRILHA, trilha.getId(), PRAZO, null), requisitante);

        assertThat(response.status()).isEqualTo(StatusAcaoPdi.CONCLUIDA);
        assertThat(response.concluidoEm()).isEqualTo(concluida.getConcluidoEm());
    }

    @Test
    void trilhaInativaNaoPodeSerLigadaAoPdi() {
        prepararItemGerenciavel();
        trilha.setAtivo(false);
        when(trilhaRepository.findById(trilha.getId())).thenReturn(Optional.of(trilha));

        assertThatThrownBy(() -> pdiAcaoService.adicionar(pdi.getId(), item.getId(),
                new PdiAcaoRequest(TipoAcaoPdi.TRILHA, trilha.getId(), PRAZO, null), requisitante))
                .isInstanceOf(OperacaoNaoPermitidaException.class);
        verify(pdiAcaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void trilhaDeOutroTenantLancaResourceNotFound() {
        prepararItemGerenciavel();
        Tenant outro = new Tenant();
        ReflectionTestUtils.setField(outro, "id", UUID.randomUUID());
        trilha.setTenant(outro);
        when(trilhaRepository.findById(trilha.getId())).thenReturn(Optional.of(trilha));

        assertThatThrownBy(() -> pdiAcaoService.adicionar(pdi.getId(), item.getId(),
                new PdiAcaoRequest(TipoAcaoPdi.TRILHA, trilha.getId(), PRAZO, null), requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void itemDeOutroPdiLancaResourceNotFound() {
        Pdi outroPdi = Pdi.novo(colaborador, gestor, "2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        ReflectionTestUtils.setField(outroPdi, "id", UUID.randomUUID());
        when(pdiService.buscarEditavel(outroPdi.getId(), requisitante)).thenReturn(outroPdi);
        when(pdiItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> pdiAcaoService.adicionar(outroPdi.getId(), item.getId(),
                new PdiAcaoRequest(TipoAcaoPdi.MENTORIA, null, PRAZO, "Mentoria"), requisitante))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void acaoDeMentoriaTemStatusManual() {
        prepararItemGerenciavel();
        PdiAcao mentoria = PdiAcao.descritiva(item, TipoAcaoPdi.MENTORIA, PRAZO, "Mentoria com a gestora");
        UUID acaoId = UUID.randomUUID();
        ReflectionTestUtils.setField(mentoria, "id", acaoId);
        when(pdiAcaoRepository.findById(acaoId)).thenReturn(Optional.of(mentoria));
        when(pdiAcaoRepository.saveAndFlush(mentoria)).thenReturn(mentoria);

        PdiAcaoResponse response = pdiAcaoService.atualizarStatus(pdi.getId(), item.getId(), acaoId,
                new AtualizarStatusAcaoRequest(StatusAcaoPdi.CONCLUIDA), requisitante);

        assertThat(response.status()).isEqualTo(StatusAcaoPdi.CONCLUIDA);
        assertThat(response.concluidoEm()).isNotNull();
        assertThat(response.progressoTrilha()).isNull();
    }

    @Test
    void statusDeAcaoDeTrilhaNaoMudaManualmente() {
        prepararItemGerenciavel();
        PdiAcao deTrilha = PdiAcao.deTrilha(item, trilha, PRAZO, null);
        UUID acaoId = UUID.randomUUID();
        ReflectionTestUtils.setField(deTrilha, "id", acaoId);
        when(pdiAcaoRepository.findById(acaoId)).thenReturn(Optional.of(deTrilha));

        assertThatThrownBy(() -> pdiAcaoService.atualizarStatus(pdi.getId(), item.getId(), acaoId,
                new AtualizarStatusAcaoRequest(StatusAcaoPdi.CONCLUIDA), requisitante))
                .isInstanceOf(OperacaoNaoPermitidaException.class);
        verify(pdiAcaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void concluirATrilhaConcluiAsAcoesLigadas() {
        PdiAcao acao = PdiAcao.deTrilha(item, trilha, PRAZO, null);
        OffsetDateTime concluidoEm = OffsetDateTime.now();
        when(pdiAcaoRepository.acoesDeTrilhaEmAberto(tenant.getId(), trilha.getId(), colaborador.getId()))
                .thenReturn(List.of(acao));

        pdiAcaoService.aoConcluirTrilha(
                new TrilhaConcluidaEvent(tenant.getId(), trilha.getId(), colaborador.getId(), concluidoEm));

        assertThat(acao.getStatus()).isEqualTo(StatusAcaoPdi.CONCLUIDA);
        assertThat(acao.getConcluidoEm()).isEqualTo(concluidoEm);
        verify(pdiAcaoRepository).saveAll(List.of(acao));
    }

    private void prepararItemGerenciavel() {
        when(pdiService.buscarEditavel(pdi.getId(), requisitante)).thenReturn(pdi);
        when(pdiItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
    }

    private TrilhaAtribuicao atribuicao(StatusAtribuicao status) {
        TrilhaAtribuicao atribuicao = new TrilhaAtribuicao();
        atribuicao.setTenant(tenant);
        atribuicao.setTrilha(trilha);
        atribuicao.setUsuario(colaborador);
        atribuicao.setAtribuidoPor(colaborador);
        atribuicao.setStatus(status);
        ReflectionTestUtils.setField(atribuicao, "id", UUID.randomUUID());
        return atribuicao;
    }

    private Usuario usuario(Perfil papel) {
        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto(papel.name());
        usuario.setEmail(UUID.randomUUID() + "@empresa.com");
        usuario.setSenhaHash("hash");
        usuario.setPapel(papel);
        usuario.setAtivo(true);
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        return usuario;
    }
}
