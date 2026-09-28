package com.nexo.service;

import com.nexo.dto.area.AreaRequest;
import com.nexo.dto.area.AreaResponse;
import com.nexo.entity.Area;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.exception.NomeDuplicadoException;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio do catálogo de Áreas (departamentos).
 *
 * <p><strong>Isolamento por tenant:</strong> segue o mesmo contrato do
 * {@link UsuarioService} — o tenant vem sempre do usuário autenticado
 * ({@code requisitanteEmail}), nunca de um parâmetro do cliente. Uma área de
 * outro tenant responde {@link ResourceNotFoundException} (404, nunca 403),
 * para não revelar que aquele registro existe em outra empresa.</p>
 */
@Service
public class AreaService {

    private final AreaRepository areaRepository;
    private final UsuarioRepository usuarioRepository;

    public AreaService(AreaRepository areaRepository, UsuarioRepository usuarioRepository) {
        this.areaRepository = areaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public AreaResponse criar(AreaRequest request, String requisitanteEmail) {
        Tenant tenant = tenantDoRequisitante(requisitanteEmail);
        String nome = request.nome().trim();
        exigirNomeDisponivel(tenant.getId(), nome, null);

        Area area = new Area();
        area.setTenant(tenant);
        area.setNome(nome);
        area.setAtivo(true);
        return AreaResponse.from(gravar(area, nome));
    }

    @Transactional(readOnly = true)
    public List<AreaResponse> listar(boolean incluirInativas, String requisitanteEmail) {
        UUID tenantId = tenantDoRequisitante(requisitanteEmail).getId();
        List<Area> areas = incluirInativas
                ? areaRepository.findByTenantIdOrderByNome(tenantId)
                : areaRepository.findByTenantIdAndAtivoTrueOrderByNome(tenantId);
        return areas.stream().map(AreaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AreaResponse buscarPorId(UUID id, String requisitanteEmail) {
        return AreaResponse.from(buscarEntidadeNoTenant(id, requisitanteEmail));
    }

    @Transactional
    public AreaResponse atualizar(UUID id, AreaRequest request, String requisitanteEmail) {
        Area area = buscarEntidadeNoTenant(id, requisitanteEmail);
        String nome = request.nome().trim();
        exigirNomeDisponivel(area.getTenant().getId(), nome, area.getId());

        area.setNome(nome);
        return AreaResponse.from(gravar(area, nome));
    }

    /**
     * Inativa a área (soft delete via {@code Area.ativo}). Uma área ainda
     * vinculada a usuários não pode ser inativada: a coluna {@code usuario.area_id}
     * continuaria apontando para um departamento que o cadastro não oferece mais.
     */
    @Transactional
    public AreaResponse inativar(UUID id, String requisitanteEmail) {
        Area area = buscarEntidadeNoTenant(id, requisitanteEmail);

        if (usuarioRepository.existsByAreaId(area.getId())) {
            throw new OperacaoNaoPermitidaException(
                    "Área com usuários vinculados não pode ser inativada: " + area.getNome());
        }

        area.setAtivo(false);
        return AreaResponse.from(areaRepository.saveAndFlush(area));
    }

    @Transactional
    public AreaResponse ativar(UUID id, String requisitanteEmail) {
        Area area = buscarEntidadeNoTenant(id, requisitanteEmail);
        area.setAtivo(true);
        return AreaResponse.from(areaRepository.saveAndFlush(area));
    }

    /**
     * Grava traduzindo a violação da UNIQUE {@code uq_area_tenant_nome} em erro
     * de negócio. É a segunda camada de proteção contra nome duplicado: cobre a
     * corrida entre a checagem e a gravação. O {@code saveAndFlush} é essencial —
     * como o id é gerado em memória, um {@code save} comum só dispararia o INSERT
     * no commit, depois deste método ter retornado, e a violação nunca cairia
     * neste catch.
     */
    private Area gravar(Area area, String nome) {
        try {
            return areaRepository.saveAndFlush(area);
        } catch (DataIntegrityViolationException ex) {
            throw new NomeDuplicadoException("uma área", nome);
        }
    }

    private void exigirNomeDisponivel(UUID tenantId, String nome, UUID idEmEdicao) {
        areaRepository.findByTenantIdAndNomeIgnoreCase(tenantId, nome)
                .filter(existente -> !existente.getId().equals(idEmEdicao))
                .ifPresent(existente -> {
                    throw new NomeDuplicadoException("uma área", nome);
                });
    }

    private Area buscarEntidadeNoTenant(UUID id, String requisitanteEmail) {
        Tenant tenant = tenantDoRequisitante(requisitanteEmail);
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Área não encontrada: " + id));
        if (!area.getTenant().getId().equals(tenant.getId())) {
            throw new ResourceNotFoundException("Área não encontrada: " + id);
        }
        return area;
    }

    private Tenant tenantDoRequisitante(String requisitanteEmail) {
        return usuarioRepository.findByEmailIgnoreCase(requisitanteEmail)
                .map(Usuario::getTenant)
                .orElseThrow(() -> new IllegalStateException(
                        "Usuário autenticado não encontrado na base: " + requisitanteEmail));
    }
}
