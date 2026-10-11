package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.etapa.EtapaRequest;
import com.nexo.dto.etapa.EtapaResponse;
import com.nexo.dto.etapa.ReordenarEtapasRequest;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.entity.Conteudo;
import com.nexo.entity.EtapaProgresso;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusConteudo;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoConteudo;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaEtapa;
import com.nexo.event.EtapaConcluidaEvent;
import com.nexo.event.EtapasDaTrilhaAlteradasEvent;
import com.nexo.exception.ParametroInvalidoException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.EtapaProgressoRepository;
import com.nexo.repository.TrilhaEtapaRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TrilhaEtapaServiceTest {

    @Mock
    private TrilhaEtapaRepository trilhaEtapaRepository;

    @Mock
    private EtapaProgressoRepository etapaProgressoRepository;

    @Mock
    private TrilhaRepository trilhaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ConteudoService conteudoService;

    @Mock
    private TrilhaAtribuicaoService trilhaAtribuicaoService;

    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private TrilhaEtapaService service;

    private Tenant tenant;
    private Trilha trilha;
    private Conteudo conteudo;
    private UsuarioAutenticado gestor;

    @BeforeEach
    void prepara() {
        tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        trilha = new Trilha();
        ReflectionTestUtils.setField(trilha, "id", UUID.randomUUID());
        trilha.setTenant(tenant);
        conteudo = new Conteudo();
        ReflectionTestUtils.setField(conteudo, "id", UUID.randomUUID());
        conteudo.setTitulo("Boas-vindas");
        conteudo.setTipo(TipoConteudo.TEXTO);
        conteudo.setStatus(StatusConteudo.PUBLICADO);
        gestor = new UsuarioAutenticado(UUID.randomUUID(), tenant.getId(), "Gestor", "gestor@nexo.com",
                "{bcrypt}x", Perfil.GESTOR, true);
        when(trilhaRepository.findById(trilha.getId())).thenReturn(Optional.of(trilha));
    }

    @Test
    void novaEtapaVaiParaAProximaOrdemLivreEAvisaQueAsEtapasMudaram() {
        when(conteudoService.buscarPublicadoNoTenant(conteudo.getId(), tenant.getId())).thenReturn(conteudo);
        when(trilhaEtapaRepository.maiorOrdem(trilha.getId())).thenReturn(2);
        when(trilhaEtapaRepository.saveAndFlush(any(TrilhaEtapa.class))).thenAnswer(inv -> inv.getArgument(0));

        EtapaResponse resposta = service.criar(trilha.getId(), new EtapaRequest(conteudo.getId(), " ", null), gestor);

        assertThat(resposta.ordem()).isEqualTo(3);
        assertThat(resposta.titulo()).isEqualTo("Boas-vindas");
        verify(publisher).publishEvent(new EtapasDaTrilhaAlteradasEvent(trilha.getId(), tenant.getId()));
    }

    @Test
    void trilhaDeOutroTenantRetorna404() {
        UsuarioAutenticado deFora = new UsuarioAutenticado(UUID.randomUUID(), UUID.randomUUID(), "X", "x@x.com",
                "{bcrypt}x", Perfil.ADMIN, true);

        assertThatThrownBy(() -> service.listar(trilha.getId(), deFora))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reordenarExigeExatamenteAsEtapasDaTrilha() {
        TrilhaEtapa a = etapa(1);
        TrilhaEtapa b = etapa(2);
        when(trilhaEtapaRepository.findByTrilhaIdOrderByOrdem(trilha.getId())).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> service.reordenar(trilha.getId(),
                new ReordenarEtapasRequest(List.of(a.getId(), a.getId())), gestor))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> service.reordenar(trilha.getId(),
                new ReordenarEtapasRequest(List.of(a.getId())), gestor))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void reordenarAplicaANovaSequencia() {
        TrilhaEtapa a = etapa(1);
        TrilhaEtapa b = etapa(2);
        when(trilhaEtapaRepository.findByTrilhaIdOrderByOrdem(trilha.getId())).thenReturn(List.of(a, b));

        service.reordenar(trilha.getId(), new ReordenarEtapasRequest(List.of(b.getId(), a.getId())), gestor);

        assertThat(b.getOrdem()).isEqualTo(1);
        assertThat(a.getOrdem()).isEqualTo(2);
    }

    @Test
    void removerFechaOBuracoNaSequenciaEApagaOProgressoDaEtapa() {
        TrilhaEtapa a = etapa(1);
        TrilhaEtapa b = etapa(2);
        TrilhaEtapa c = etapa(3);
        when(trilhaEtapaRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(trilhaEtapaRepository.findByTrilhaIdOrderByOrdem(trilha.getId())).thenReturn(List.of(b, c));

        service.remover(trilha.getId(), a.getId(), gestor);

        verify(etapaProgressoRepository).removerDaEtapa(a.getId());
        verify(trilhaEtapaRepository).delete(a);
        assertThat(b.getOrdem()).isEqualTo(1);
        assertThat(c.getOrdem()).isEqualTo(2);
        verify(publisher).publishEvent(new EtapasDaTrilhaAlteradasEvent(trilha.getId(), tenant.getId()));
    }

    @Test
    void concluirRegistraOProgressoDoProprioUsuarioEPublicaOEvento() {
        TrilhaEtapa a = etapa(1);
        when(trilhaEtapaRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(etapaProgressoRepository.existsByEtapaIdAndUsuarioId(a.getId(), gestor.getId())).thenReturn(false);
        when(trilhaAtribuicaoService.buscarProgresso(trilha.getId(), gestor))
                .thenReturn(ProgressoTrilhaResponse.naoIniciado(trilha));

        service.concluir(trilha.getId(), a.getId(), gestor);

        verify(trilhaAtribuicaoService).iniciar(trilha.getId(), gestor);
        verify(etapaProgressoRepository).saveAndFlush(any(EtapaProgresso.class));
        verify(publisher).publishEvent(new EtapaConcluidaEvent(a.getId(), trilha.getId(), tenant.getId(), gestor.getId()));
    }

    @Test
    void concluirDeNovoNaoDuplicaNemRecalcula() {
        TrilhaEtapa a = etapa(1);
        when(trilhaEtapaRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(etapaProgressoRepository.existsByEtapaIdAndUsuarioId(a.getId(), gestor.getId())).thenReturn(true);

        service.concluir(trilha.getId(), a.getId(), gestor);

        verify(etapaProgressoRepository, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void etapaDeOutraTrilhaRetorna404() {
        Trilha outra = new Trilha();
        ReflectionTestUtils.setField(outra, "id", UUID.randomUUID());
        outra.setTenant(tenant);
        TrilhaEtapa daOutra = TrilhaEtapa.nova(outra, conteudo, null, null, 1);
        ReflectionTestUtils.setField(daOutra, "id", UUID.randomUUID());
        when(trilhaEtapaRepository.findById(daOutra.getId())).thenReturn(Optional.of(daOutra));

        assertThatThrownBy(() -> service.concluir(trilha.getId(), daOutra.getId(), gestor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private TrilhaEtapa etapa(int ordem) {
        TrilhaEtapa etapa = TrilhaEtapa.nova(trilha, conteudo, null, null, ordem);
        ReflectionTestUtils.setField(etapa, "id", UUID.randomUUID());
        return etapa;
    }
}
