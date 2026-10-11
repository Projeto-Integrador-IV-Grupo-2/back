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
import com.nexo.repository.ConteudoRepository;
import com.nexo.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ConteudoRepository conteudoRepository;

    public AreaService(AreaRepository areaRepository, UsuarioRepository usuarioRepository,
                       ConteudoRepository conteudoRepository) {
        this.areaRepository = areaRepository;
        this.usuarioRepository = usuarioRepository;
        this.conteudoRepository = conteudoRepository;
    }

    @Transactional
    public AreaResponse criar(AreaRequest request, String requisitanteEmail) {
        Tenant tenant = tenantDoRequisitante(requisitanteEmail);
        String nome = request.nome().trim();
        exigirNomeDisponivel(tenant.getId(), nome, null);

        Area area = new Area();
        area.setTenant(tenant);
        area.setNome(nome);
        area.setDescricao(textoOuNulo(request.descricao()));
        area.setAtivo(true);
        return AreaResponse.from(gravar(area, nome), 0L, 0L);
    }

    /**
     * Página de áreas do tenant, opcionalmente filtrada por trecho do nome.
     * As contagens de pessoas e de conteúdos vêm cada uma numa única consulta
     * agrupada para a página inteira, em vez de uma por área.
     */
    @Transactional(readOnly = true)
    public Page<AreaResponse> listar(String nome, boolean incluirInativas, Pageable pageable,
                                     String requisitanteEmail) {
        UUID tenantId = tenantDoRequisitante(requisitanteEmail).getId();
        String trecho = nome == null ? "" : nome.trim();
        Page<Area> areas = incluirInativas
                ? areaRepository.findByTenantIdAndNomeContainingIgnoreCase(tenantId, trecho, pageable)
                : areaRepository.findByTenantIdAndAtivoTrueAndNomeContainingIgnoreCase(tenantId, trecho, pageable);

        List<UUID> ids = areas.getContent().stream().map(Area::getId).toList();
        Map<UUID, Long> pessoas = ids.isEmpty() ? Map.of() : porArea(usuarioRepository.contarPorArea(ids));
        Map<UUID, Long> conteudos = ids.isEmpty() ? Map.of() : porArea(conteudoRepository.contarPorArea(ids));
        return areas.map(area -> AreaResponse.from(area,
                pessoas.getOrDefault(area.getId(), 0L),
                conteudos.getOrDefault(area.getId(), 0L)));
    }

    private static Map<UUID, Long> porArea(List<UsuarioRepository.TotalPorArea> totais) {
        return totais.stream().collect(Collectors.toMap(
                UsuarioRepository.TotalPorArea::getAreaId,
                UsuarioRepository.TotalPorArea::getTotal));
    }

    @Transactional(readOnly = true)
    public AreaResponse buscarPorId(UUID id, String requisitanteEmail) {
        return comTotal(buscarEntidadeNoTenant(id, requisitanteEmail));
    }

    @Transactional
    public AreaResponse atualizar(UUID id, AreaRequest request, String requisitanteEmail) {
        Area area = buscarEntidadeNoTenant(id, requisitanteEmail);
        String nome = request.nome().trim();
        exigirNomeDisponivel(area.getTenant().getId(), nome, area.getId());

        area.setNome(nome);
        area.setDescricao(textoOuNulo(request.descricao()));
        return comTotal(gravar(area, nome));
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
        return AreaResponse.from(areaRepository.saveAndFlush(area), 0L,
                conteudoRepository.countByAreaId(area.getId()));
    }

    @Transactional
    public AreaResponse ativar(UUID id, String requisitanteEmail) {
        Area area = buscarEntidadeNoTenant(id, requisitanteEmail);
        area.setAtivo(true);
        return comTotal(areaRepository.saveAndFlush(area));
    }

    private AreaResponse comTotal(Area area) {
        return AreaResponse.from(area,
                usuarioRepository.countByAreaId(area.getId()),
                conteudoRepository.countByAreaId(area.getId()));
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
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
