package com.nexo.service;

import com.nexo.dto.categoria.CategoriaRequest;
import com.nexo.dto.categoria.CategoriaResponse;
import com.nexo.entity.Categoria;
import com.nexo.exception.NomeDuplicadoException;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.CategoriaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de categorias de conteúdo (issue #40).
 *
 * <p>Mesmo contrato de {@link AreaService}: o tenant vem sempre do usuário
 * autenticado, nome único por tenant sem diferenciar maiúsculas, e categoria
 * de outro tenant responde 404.</p>
 */
@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final TenantRepository tenantRepository;

    public CategoriaService(CategoriaRepository categoriaRepository, TenantRepository tenantRepository) {
        this.categoriaRepository = categoriaRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public CategoriaResponse criar(CategoriaRequest request, UsuarioAutenticado requisitante) {
        String nome = request.nome().trim();
        exigirNomeDisponivel(requisitante.getTenantId(), nome, null);

        Categoria categoria = new Categoria();
        categoria.setTenant(tenantRepository.getReferenceById(requisitante.getTenantId()));
        categoria.setNome(nome);
        categoria.setDescricao(textoOuNulo(request.descricao()));
        categoria.setAtivo(true);
        return CategoriaResponse.de(gravar(categoria, nome));
    }

    @Transactional(readOnly = true)
    public Page<CategoriaResponse> listar(String nome, boolean incluirInativas, Pageable pageable,
                                          UsuarioAutenticado requisitante) {
        String trecho = nome == null ? "" : nome.trim();
        Page<Categoria> categorias = incluirInativas
                ? categoriaRepository.findByTenantIdAndNomeContainingIgnoreCase(
                        requisitante.getTenantId(), trecho, pageable)
                : categoriaRepository.findByTenantIdAndAtivoTrueAndNomeContainingIgnoreCase(
                        requisitante.getTenantId(), trecho, pageable);
        return categorias.map(CategoriaResponse::de);
    }

    @Transactional
    public CategoriaResponse atualizar(UUID id, CategoriaRequest request, UsuarioAutenticado requisitante) {
        Categoria categoria = buscarNoTenant(id, requisitante.getTenantId());
        String nome = request.nome().trim();
        exigirNomeDisponivel(requisitante.getTenantId(), nome, categoria.getId());

        categoria.setNome(nome);
        categoria.setDescricao(textoOuNulo(request.descricao()));
        return CategoriaResponse.de(gravar(categoria, nome));
    }

    /** Usado pelo cadastro de conteúdo: categoria do tenant e ativa, ou erro. */
    Categoria buscarAtivaNoTenant(UUID id, UUID tenantId) {
        Categoria categoria = buscarNoTenant(id, tenantId);
        if (!categoria.isAtivo()) {
            throw new OperacaoNaoPermitidaException(
                    "Categoria inativa não pode receber conteúdos: " + categoria.getNome());
        }
        return categoria;
    }

    private Categoria buscarNoTenant(UUID id, UUID tenantId) {
        return categoriaRepository.findById(id)
                .filter(categoria -> categoria.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: " + id));
    }

    /** Segunda camada contra nome duplicado: cobre a corrida entre a checagem e o INSERT. */
    private Categoria gravar(Categoria categoria, String nome) {
        try {
            return categoriaRepository.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException ex) {
            throw new NomeDuplicadoException("uma categoria", nome);
        }
    }

    private void exigirNomeDisponivel(UUID tenantId, String nome, UUID idEmEdicao) {
        categoriaRepository.findByTenantIdAndNomeIgnoreCase(tenantId, nome)
                .filter(existente -> !existente.getId().equals(idEmEdicao))
                .ifPresent(existente -> {
                    throw new NomeDuplicadoException("uma categoria", nome);
                });
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
