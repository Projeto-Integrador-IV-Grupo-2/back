package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexo.dto.atribuicao.ColaboradorAcompanhamentoResponse;
import com.nexo.entity.Perfil;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Tenant;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.exception.ParametroInvalidoException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AtribuicaoFiltro;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.exportacao.ExportadorCsv;
import com.nexo.service.exportacao.ExportadorFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AcompanhamentoServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 5);

    @Mock
    private TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private AcompanhamentoService service;
    private Tenant tenant;
    private UsuarioAutenticado gestor;
    private Usuario colaborador;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        service = new AcompanhamentoService(trilhaAtribuicaoRepository, usuarioRepository,
                new RegraDeAtraso(relogio), new ExportadorFactory(List.of(new ExportadorCsv())));

        tenant = tenantComId();
        gestor = new UsuarioAutenticado(UUID.randomUUID(), tenant.getId(), "Gestora", "gestora@nexo.com",
                "hash", Perfil.GESTOR, true);
        colaborador = new Usuario();
        ReflectionTestUtils.setField(colaborador, "id", UUID.randomUUID());
        colaborador.setTenant(tenant);
        colaborador.setNomeCompleto("Ana Souza");
        colaborador.setEmail("ana@nexo.com");
    }

    @Test
    void listagemDevolveStatusEfetivoEPercentualGravado() {
        TrilhaAtribuicao vencida = atribuicao(StatusAtribuicao.EM_ANDAMENTO, HOJE.minusDays(2));
        vencida.setPercentual(50);
        when(trilhaAtribuicaoRepository.findAll(anySpec(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(vencida)));

        var pagina = service.listar(AtribuicaoFiltro.vazio(), PageRequest.of(0, 20), gestor);

        assertThat(pagina.getContent()).singleElement().satisfies(linha -> {
            assertThat(linha.status()).isEqualTo(StatusAtribuicao.ATRASADA);
            assertThat(linha.percentual()).isEqualTo(50);
            assertThat(linha.colaborador().email()).isEqualTo("ana@nexo.com");
        });
    }

    @Test
    void visaoDoColaboradorTrazTotaisQueBatemComALista() {
        when(usuarioRepository.findById(colaborador.getId())).thenReturn(Optional.of(colaborador));
        when(trilhaAtribuicaoRepository.findAll(anySpec(), any(Sort.class))).thenReturn(List.of(
                atribuicao(StatusAtribuicao.CONCLUIDA, HOJE.minusDays(10)),
                atribuicao(StatusAtribuicao.EM_ANDAMENTO, HOJE.plusDays(3)),
                atribuicao(StatusAtribuicao.EM_ANDAMENTO, HOJE.minusDays(1))));

        ColaboradorAcompanhamentoResponse visao = service.colaborador(colaborador.getId(), gestor);

        assertThat(visao.trilhas()).hasSize(3);
        assertThat(visao.totais().total()).isEqualTo(3);
        assertThat(visao.totais().concluidas()).isEqualTo(1);
        assertThat(visao.totais().emAndamento()).isEqualTo(1);
        assertThat(visao.totais().atrasadas()).isEqualTo(1);
        assertThat(visao.totais().naoIniciadas()).isZero();
    }

    @Test
    void colaboradorDeOutroTenantRetorna404() {
        colaborador.setTenant(tenantComId());
        when(usuarioRepository.findById(colaborador.getId())).thenReturn(Optional.of(colaborador));

        assertThatThrownBy(() -> service.colaborador(colaborador.getId(), gestor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void colaboradorInexistenteRetorna404() {
        UUID qualquer = UUID.randomUUID();
        when(usuarioRepository.findById(qualquer)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.colaborador(qualquer, gestor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void exportarComFormatoDesconhecidoNemConsultaOBanco() {
        assertThatThrownBy(() -> service.exportar(AtribuicaoFiltro.vazio(), Sort.unsorted(), "xls", gestor))
                .isInstanceOf(ParametroInvalidoException.class);
        verifyNoInteractions(trilhaAtribuicaoRepository);
    }

    @Test
    void exportarGeraUmaLinhaPorAtribuicao() {
        when(trilhaAtribuicaoRepository.findAll(anySpec(), any(Sort.class))).thenReturn(List.of(
                atribuicao(StatusAtribuicao.CONCLUIDA, null),
                atribuicao(StatusAtribuicao.EM_ANDAMENTO, null)));

        var arquivo = service.exportar(AtribuicaoFiltro.vazio(), Sort.unsorted(), "csv", gestor);

        String csv = new String(arquivo.conteudo(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv.split("\r\n")).hasSize(3);
    }

    private TrilhaAtribuicao atribuicao(StatusAtribuicao status, LocalDate prazo) {
        Trilha trilha = new Trilha();
        ReflectionTestUtils.setField(trilha, "id", UUID.randomUUID());
        trilha.setTenant(tenant);
        trilha.setNome("Trilha " + status);

        TrilhaAtribuicao atribuicao = new TrilhaAtribuicao();
        ReflectionTestUtils.setField(atribuicao, "id", UUID.randomUUID());
        atribuicao.setTenant(tenant);
        atribuicao.setTrilha(trilha);
        atribuicao.setUsuario(colaborador);
        atribuicao.setStatus(status);
        atribuicao.setPrazo(prazo);
        return atribuicao;
    }

    private static Tenant tenantComId() {
        Tenant t = new Tenant();
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }

    @SuppressWarnings("unchecked")
    private static Specification<TrilhaAtribuicao> anySpec() {
        return any(Specification.class);
    }
}
