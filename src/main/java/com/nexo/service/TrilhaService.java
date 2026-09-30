package com.nexo.service;

import com.nexo.dto.trilha.CriarTrilhaRequest;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.dto.trilha.TrilhaDetalheResponse;
import com.nexo.dto.trilha.TrilhaResumoResponse;
import com.nexo.entity.Area;
import com.nexo.entity.Trilha;
import com.nexo.entity.Usuario;
import com.nexo.exception.NomeDuplicadoException;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta e cadastro de trilhas (Cards 05 e 08).
 *
 * <p><strong>Isolamento por tenant:</strong> o escopo é sempre o tenant do
 * {@link UsuarioAutenticado} do contexto de segurança — nunca um parâmetro
 * vindo do cliente. Trilha inexistente ou de outro tenant sempre resulta em
 * {@link ResourceNotFoundException} (404), nunca 403: um usuário não pode
 * descobrir, pela resposta, que uma trilha existe em outra empresa.</p>
 */
@Service
public class TrilhaService {

    private final TrilhaRepository trilhaRepository;
    private final TrilhaAtribuicaoService trilhaAtribuicaoService;
    private final AreaRepository areaRepository;
    private final UsuarioRepository usuarioRepository;

    public TrilhaService(TrilhaRepository trilhaRepository,
                         TrilhaAtribuicaoService trilhaAtribuicaoService,
                         AreaRepository areaRepository,
                         UsuarioRepository usuarioRepository) {
        this.trilhaRepository = trilhaRepository;
        this.trilhaAtribuicaoService = trilhaAtribuicaoService;
        this.areaRepository = areaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Trilhas ativas do tenant do requisitante (Card 05), cada uma com o
     * progresso dele. Inativas ficam de fora: a listagem é o catálogo do que o
     * colaborador pode fazer agora.
     */
    @Transactional(readOnly = true)
    public Page<TrilhaResumoResponse> listar(Pageable pageable, UsuarioAutenticado requisitante) {
        Page<Trilha> trilhas = trilhaRepository.findByTenantIdAndAtivoTrue(requisitante.getTenantId(), pageable);
        Map<UUID, ProgressoTrilhaResponse> progressos = trilhas.isEmpty()
                ? Map.of()
                : trilhaAtribuicaoService.progressosDoRequisitante(requisitante);
        return trilhas.map(trilha -> {
            ProgressoTrilhaResponse progresso = progressos.get(trilha.getId());
            return progresso == null ? TrilhaResumoResponse.de(trilha) : TrilhaResumoResponse.de(trilha, progresso);
        });
    }

    /**
     * Trilha completa + progresso do requisitante nela (ou "não iniciado",
     * quando ele nunca acessou — ver {@link TrilhaAtribuicaoService#buscarProgressoOuNaoIniciado}).
     */
    @Transactional(readOnly = true)
    public TrilhaDetalheResponse buscarDetalhe(UUID trilhaId, UsuarioAutenticado requisitante) {
        Trilha trilha = buscarEntidadeNoTenant(trilhaId, requisitante.getTenantId());
        ProgressoTrilhaResponse progresso = trilhaAtribuicaoService.buscarProgressoOuNaoIniciado(trilha, requisitante);
        return TrilhaDetalheResponse.de(trilha, progresso);
    }

    /**
     * Cadastra uma trilha no tenant do requisitante, que fica registrado como
     * autor. O nome é único por tenant (sem diferenciar maiúsculas); a área,
     * quando informada, precisa ser uma área ativa do mesmo tenant.
     */
    @Transactional
    public TrilhaResumoResponse criar(CriarTrilhaRequest request, UsuarioAutenticado requisitante) {
        String nome = request.nome().trim();
        if (trilhaRepository.existsByTenantIdAndNomeIgnoreCase(requisitante.getTenantId(), nome)) {
            throw new NomeDuplicadoException("uma trilha", nome);
        }

        Usuario autor = usuarioRepository.findById(requisitante.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuário autenticado não encontrado na base: " + requisitante.getEmail()));

        Trilha trilha = new Trilha();
        trilha.setTenant(autor.getTenant());
        trilha.setNome(nome);
        trilha.setArea(resolverArea(request.areaId(), requisitante.getTenantId()));
        trilha.setPrazoDias(request.prazoDias());
        trilha.setObjetivos(textoOuNulo(request.objetivos()));
        trilha.setDescricao(textoOuNulo(request.descricao()));
        trilha.setCriadoPor(autor);
        trilha.setAtivo(true);
        return TrilhaResumoResponse.de(trilhaRepository.saveAndFlush(trilha));
    }

    private Area resolverArea(UUID areaId, UUID tenantId) {
        if (areaId == null) {
            return null;
        }
        Area area = areaRepository.findById(areaId)
                .filter(a -> a.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Área não encontrada: " + areaId));
        if (!area.isAtivo()) {
            throw new OperacaoNaoPermitidaException("Área inativa não pode receber novas trilhas: " + area.getNome());
        }
        return area;
    }

    private Trilha buscarEntidadeNoTenant(UUID trilhaId, UUID tenantId) {
        Trilha trilha = trilhaRepository.findById(trilhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Trilha não encontrada: " + trilhaId));
        if (!trilha.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Trilha não encontrada: " + trilhaId);
        }
        return trilha;
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
