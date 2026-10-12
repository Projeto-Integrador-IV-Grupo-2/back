package com.nexo.service;

import com.nexo.dto.competencia.CompetenciaRequest;
import com.nexo.dto.competencia.CompetenciaResponse;
import com.nexo.entity.Competencia;
import com.nexo.exception.NomeDuplicadoException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.CompetenciaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio do catálogo de competências usado pelos PDIs.
 *
 * <p>Bean sem estado (Singleton do Spring): tudo o que muda entre chamadas
 * vem por parâmetro. O tenant vem sempre do usuário autenticado; competência
 * de outro tenant responde 404, como no catálogo de Áreas.</p>
 */
@Service
public class CompetenciaService {

    private static final String RECURSO = "uma competência";

    private final CompetenciaRepository competenciaRepository;
    private final TenantRepository tenantRepository;

    public CompetenciaService(CompetenciaRepository competenciaRepository, TenantRepository tenantRepository) {
        this.competenciaRepository = competenciaRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public CompetenciaResponse criar(CompetenciaRequest request, UsuarioAutenticado requisitante) {
        String nome = request.nome().trim();
        exigirNomeDisponivel(requisitante.getTenantId(), nome, null);

        Competencia competencia = new Competencia();
        competencia.setTenant(tenantRepository.getReferenceById(requisitante.getTenantId()));
        aplicar(competencia, nome, request);
        competencia.setAtivo(true);
        return CompetenciaResponse.de(gravar(competencia, nome));
    }

    @Transactional(readOnly = true)
    public Page<CompetenciaResponse> listar(String nome, boolean incluirInativas, Pageable pageable,
                                            UsuarioAutenticado requisitante) {
        String trecho = nome == null ? "" : nome.trim();
        UUID tenantId = requisitante.getTenantId();
        Page<Competencia> competencias = incluirInativas
                ? competenciaRepository.findByTenantIdAndNomeContainingIgnoreCase(tenantId, trecho, pageable)
                : competenciaRepository.findByTenantIdAndAtivoTrueAndNomeContainingIgnoreCase(
                        tenantId, trecho, pageable);
        return competencias.map(CompetenciaResponse::de);
    }

    @Transactional(readOnly = true)
    public CompetenciaResponse buscarPorId(UUID id, UsuarioAutenticado requisitante) {
        return CompetenciaResponse.de(buscarNoTenant(id, requisitante.getTenantId()));
    }

    @Transactional
    public CompetenciaResponse atualizar(UUID id, CompetenciaRequest request, UsuarioAutenticado requisitante) {
        Competencia competencia = buscarNoTenant(id, requisitante.getTenantId());
        String nome = request.nome().trim();
        exigirNomeDisponivel(requisitante.getTenantId(), nome, competencia.getId());

        aplicar(competencia, nome, request);
        return CompetenciaResponse.de(gravar(competencia, nome));
    }

    /**
     * Inativa a competência: ela some da listagem padrão e não pode entrar em
     * PDIs novos, mas os itens que já a usam continuam válidos.
     */
    @Transactional
    public CompetenciaResponse inativar(UUID id, UsuarioAutenticado requisitante) {
        return mudarSituacao(id, false, requisitante);
    }

    @Transactional
    public CompetenciaResponse ativar(UUID id, UsuarioAutenticado requisitante) {
        return mudarSituacao(id, true, requisitante);
    }

    /** Usado pelo PDI ao incluir um item: 404 se não for do tenant do requisitante. */
    @Transactional(readOnly = true)
    public Competencia buscarNoTenant(UUID id, UUID tenantId) {
        return competenciaRepository.findById(id)
                .filter(competencia -> competencia.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Competência não encontrada: " + id));
    }

    private CompetenciaResponse mudarSituacao(UUID id, boolean ativo, UsuarioAutenticado requisitante) {
        Competencia competencia = buscarNoTenant(id, requisitante.getTenantId());
        competencia.setAtivo(ativo);
        return CompetenciaResponse.de(competenciaRepository.saveAndFlush(competencia));
    }

    private static void aplicar(Competencia competencia, String nome, CompetenciaRequest request) {
        competencia.setNome(nome);
        competencia.setDescricao(request.descricao() == null || request.descricao().isBlank()
                ? null
                : request.descricao().trim());
        competencia.setTipo(request.tipo());
    }

    /**
     * Segunda camada contra nome duplicado (corrida entre a checagem e a
     * gravação): traduz a UNIQUE {@code uq_competencia_tenant_nome}. O
     * {@code saveAndFlush} força o INSERT aqui dentro, como em {@code AreaService}.
     */
    private Competencia gravar(Competencia competencia, String nome) {
        try {
            return competenciaRepository.saveAndFlush(competencia);
        } catch (DataIntegrityViolationException ex) {
            throw new NomeDuplicadoException(RECURSO, nome);
        }
    }

    private void exigirNomeDisponivel(UUID tenantId, String nome, UUID idEmEdicao) {
        competenciaRepository.findByTenantIdAndNomeIgnoreCase(tenantId, nome)
                .filter(existente -> !existente.getId().equals(idEmEdicao))
                .ifPresent(existente -> {
                    throw new NomeDuplicadoException(RECURSO, nome);
                });
    }
}
