package com.nexo.service.recomendacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nexo.config.IaProperties;
import com.nexo.entity.Area;
import com.nexo.entity.Cargo;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.repository.PdiItemRepository;
import com.nexo.repository.PdiRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** Montagem do catálogo e do perfil da sugestão de trilhas (issue #63). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ColetorDeDadosDaSugestaoTest {

    private static final IaProperties PROPRIEDADES = new IaProperties(
            "fake", null, null, null, null, null, null, null, null, null, null,
            new IaProperties.Sugestao(5, 3, Duration.ofMinutes(15)));

    @Mock
    private TrilhaRepository trilhaRepository;

    @Mock
    private TrilhaAtribuicaoRepository atribuicaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PdiRepository pdiRepository;

    @Mock
    private PdiItemRepository pdiItemRepository;

    private ColetorDeDadosDaSugestao coletor;
    private Tenant tenant;
    private Area suporte;
    private UsuarioAutenticado requisitante;

    @BeforeEach
    void prepara() {
        coletor = new ColetorDeDadosDaSugestao(trilhaRepository, atribuicaoRepository, usuarioRepository,
                pdiRepository, pdiItemRepository, PROPRIEDADES);
        tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", UUID.randomUUID());
        suporte = area("Suporte");
        requisitante = new UsuarioAutenticado(UUID.randomUUID(), tenant.getId(), "Pessoa", "p@nexo.com",
                "{bcrypt}x", Perfil.COLABORADOR, true);
        when(pdiRepository.findFirstByTenantIdAndColaboradorIdAndStatusOrderByInicioDescCriadoEmDesc(
                any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void oCatalogoDeixaDeForaAsTrilhasJaConcluidas() {
        Trilha atendimento = trilha("Atendimento ao Cliente", suporte);
        Trilha concluida = trilha("Onboarding", suporte);
        dadoUsuarioComAreaECargo();
        when(trilhaRepository.findByTenantIdAndAtivoTrueOrderByNome(tenant.getId()))
                .thenReturn(List.of(atendimento, concluida));
        when(atribuicaoRepository.findByTenantIdAndUsuarioId(tenant.getId(), requisitante.getId()))
                .thenReturn(List.of(atribuicao(concluida, StatusAtribuicao.CONCLUIDA)));

        DadosParaSugestao dados = coletor.coletar(requisitante);

        assertThat(dados.catalogo()).extracting(TrilhaCandidata::id).containsExactly(atendimento.getId());
        assertThat(dados.catalogo().get(0).areaNome()).isEqualTo("Suporte");
    }

    @Test
    void oCatalogoRespeitaOTetoDeTrilhasEnviadasAoModelo() {
        List<Trilha> muitas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            muitas.add(trilha("Trilha " + i, suporte));
        }
        dadoUsuarioComAreaECargo();
        when(trilhaRepository.findByTenantIdAndAtivoTrueOrderByNome(tenant.getId())).thenReturn(muitas);
        when(atribuicaoRepository.findByTenantIdAndUsuarioId(any(), any())).thenReturn(List.of());

        // IA_SUGESTAO_CATALOGO_MAXIMO = 3 nesta configuração de teste.
        assertThat(coletor.coletar(requisitante).catalogo()).hasSize(3);
    }

    @Test
    void oPerfilSeparaConcluidasDeEmAndamentoEIgnoraAsNaoIniciadas() {
        Trilha concluida = trilha("Onboarding", suporte);
        Trilha emAndamento = trilha("LGPD", null);
        Trilha naoIniciada = trilha("Negociação", null);
        dadoUsuarioComAreaECargo();
        when(trilhaRepository.findByTenantIdAndAtivoTrueOrderByNome(any())).thenReturn(List.of());
        when(atribuicaoRepository.findByTenantIdAndUsuarioId(any(), any())).thenReturn(List.of(
                atribuicao(concluida, StatusAtribuicao.CONCLUIDA),
                atribuicao(emAndamento, StatusAtribuicao.EM_ANDAMENTO),
                atribuicao(naoIniciada, StatusAtribuicao.NAO_INICIADA)));

        PerfilDoUsuario perfil = coletor.coletar(requisitante).perfil();

        assertThat(perfil.trilhasConcluidas()).containsExactly("Onboarding");
        assertThat(perfil.trilhasEmAndamento()).containsExactly("LGPD");
        assertThat(perfil.area()).isEqualTo("Suporte");
        assertThat(perfil.cargo()).isEqualTo("Analista");
    }

    @Test
    void usuarioSemAreaNemCargoNaoQuebraAColeta() {
        Usuario semNada = new Usuario();
        ReflectionTestUtils.setField(semNada, "id", requisitante.getId());
        semNada.setTenant(tenant);
        when(usuarioRepository.findById(requisitante.getId())).thenReturn(Optional.of(semNada));
        when(trilhaRepository.findByTenantIdAndAtivoTrueOrderByNome(any())).thenReturn(List.of());
        when(atribuicaoRepository.findByTenantIdAndUsuarioId(any(), any())).thenReturn(List.of());

        DadosParaSugestao dados = coletor.coletar(requisitante);

        assertThat(dados.perfil().area()).isNull();
        assertThat(dados.perfil().cargo()).isNull();
        assertThat(dados.contexto().areaDoUsuarioId()).isNull();
    }

    private void dadoUsuarioComAreaECargo() {
        Cargo analista = new Cargo();
        ReflectionTestUtils.setField(analista, "id", UUID.randomUUID());
        analista.setTenant(tenant);
        analista.setNome("Analista");

        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", requisitante.getId());
        usuario.setTenant(tenant);
        usuario.setNomeCompleto("Pessoa");
        usuario.setEmail(requisitante.getEmail());
        usuario.setArea(suporte);
        usuario.setCargo(analista);
        when(usuarioRepository.findById(requisitante.getId())).thenReturn(Optional.of(usuario));
    }

    private Area area(String nome) {
        Area nova = new Area();
        ReflectionTestUtils.setField(nova, "id", UUID.randomUUID());
        nova.setTenant(tenant);
        nova.setNome(nome);
        return nova;
    }

    private Trilha trilha(String nome, Area area) {
        Trilha nova = new Trilha();
        ReflectionTestUtils.setField(nova, "id", UUID.randomUUID());
        nova.setTenant(tenant);
        nova.setNome(nome);
        nova.setArea(area);
        return nova;
    }

    private TrilhaAtribuicao atribuicao(Trilha trilha, StatusAtribuicao status) {
        TrilhaAtribuicao nova = new TrilhaAtribuicao();
        nova.setTenant(tenant);
        nova.setTrilha(trilha);
        nova.setStatus(status);
        return nova;
    }
}
