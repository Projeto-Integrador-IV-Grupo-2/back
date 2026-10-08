package com.nexo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.dto.competencia.CompetenciaRequest;
import com.nexo.dto.competencia.CompetenciaResponse;
import com.nexo.entity.Competencia;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoCompetencia;
import com.nexo.exception.NomeDuplicadoException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.CompetenciaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompetenciaServiceTest {

    @Mock
    private CompetenciaRepository competenciaRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private CompetenciaService competenciaService;

    private Tenant tenant;
    private UsuarioAutenticado gestor;

    @BeforeEach
    void setUp() {
        tenant = tenantComId();
        gestor = new UsuarioAutenticado(UUID.randomUUID(), tenant.getId(), "Gestora", "gestora@empresa.com",
                "hash", Perfil.GESTOR, true);
    }

    @Test
    void criaCompetenciaAtivaNoTenantDoRequisitante() {
        when(competenciaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Comunicação"))
                .thenReturn(Optional.empty());
        when(tenantRepository.getReferenceById(tenant.getId())).thenReturn(tenant);
        when(competenciaRepository.saveAndFlush(any(Competencia.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompetenciaResponse response = competenciaService.criar(
                new CompetenciaRequest("  Comunicação ", "  ", TipoCompetencia.COMPORTAMENTAL), gestor);

        assertThat(response.nome()).isEqualTo("Comunicação");
        assertThat(response.descricao()).isNull();
        assertThat(response.tipo()).isEqualTo(TipoCompetencia.COMPORTAMENTAL);
        assertThat(response.ativo()).isTrue();
    }

    @Test
    void criarComNomeJaUsadoNoTenantLancaNomeDuplicado() {
        when(competenciaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Java"))
                .thenReturn(Optional.of(competencia("java", true)));

        assertThatThrownBy(() -> competenciaService.criar(
                new CompetenciaRequest("Java", null, TipoCompetencia.TECNICA), gestor))
                .isInstanceOf(NomeDuplicadoException.class);

        verify(competenciaRepository, never()).saveAndFlush(any());
    }

    @Test
    void corridaNaGravacaoViraNomeDuplicado() {
        when(competenciaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Java"))
                .thenReturn(Optional.empty());
        when(tenantRepository.getReferenceById(tenant.getId())).thenReturn(tenant);
        when(competenciaRepository.saveAndFlush(any(Competencia.class)))
                .thenThrow(new DataIntegrityViolationException("uq_competencia_tenant_nome"));

        assertThatThrownBy(() -> competenciaService.criar(
                new CompetenciaRequest("Java", null, TipoCompetencia.TECNICA), gestor))
                .isInstanceOf(NomeDuplicadoException.class);
    }

    @Test
    void editarMantendoOProprioNomeNaoEhDuplicidade() {
        Competencia existente = competencia("Java", true);
        when(competenciaRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(competenciaRepository.findByTenantIdAndNomeIgnoreCase(tenant.getId(), "Java"))
                .thenReturn(Optional.of(existente));
        when(competenciaRepository.saveAndFlush(existente)).thenReturn(existente);

        CompetenciaResponse response = competenciaService.atualizar(existente.getId(),
                new CompetenciaRequest("Java", "Back-end", TipoCompetencia.TECNICA), gestor);

        assertThat(response.descricao()).isEqualTo("Back-end");
    }

    @Test
    void inativarEAtivarMudamASituacao() {
        Competencia existente = competencia("Java", true);
        when(competenciaRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(competenciaRepository.saveAndFlush(existente)).thenReturn(existente);

        assertThat(competenciaService.inativar(existente.getId(), gestor).ativo()).isFalse();
        assertThat(competenciaService.ativar(existente.getId(), gestor).ativo()).isTrue();
    }

    @Test
    void competenciaDeOutroTenantLancaResourceNotFound() {
        Competencia deOutro = competencia("Java", true);
        deOutro.setTenant(tenantComId());
        when(competenciaRepository.findById(deOutro.getId())).thenReturn(Optional.of(deOutro));

        assertThatThrownBy(() -> competenciaService.buscarPorId(deOutro.getId(), gestor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Competencia competencia(String nome, boolean ativo) {
        Competencia competencia = new Competencia();
        competencia.setTenant(tenant);
        competencia.setNome(nome);
        competencia.setTipo(TipoCompetencia.TECNICA);
        competencia.setAtivo(ativo);
        ReflectionTestUtils.setField(competencia, "id", UUID.randomUUID());
        return competencia;
    }

    private Tenant tenantComId() {
        Tenant t = new Tenant();
        t.setNome("Empresa " + UUID.randomUUID());
        t.setSubdominio("emp-" + UUID.randomUUID());
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }
}
