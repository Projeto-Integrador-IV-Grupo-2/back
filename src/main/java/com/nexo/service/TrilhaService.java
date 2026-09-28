package com.nexo.service;

import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.dto.trilha.TrilhaDetalheResponse;
import com.nexo.dto.trilha.TrilhaResumoResponse;
import com.nexo.entity.Trilha;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.TrilhaRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de trilhas (Card 08).
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

    public TrilhaService(TrilhaRepository trilhaRepository, TrilhaAtribuicaoService trilhaAtribuicaoService) {
        this.trilhaRepository = trilhaRepository;
        this.trilhaAtribuicaoService = trilhaAtribuicaoService;
    }

    /**
     * Trilhas ativas do tenant do requisitante (Card 05). Inativas ficam de
     * fora: a listagem é o catálogo do que o colaborador pode fazer agora.
     */
    @Transactional(readOnly = true)
    public Page<TrilhaResumoResponse> listar(Pageable pageable, UsuarioAutenticado requisitante) {
        return trilhaRepository
                .findByTenantIdAndAtivoTrue(requisitante.getTenantId(), pageable)
                .map(TrilhaResumoResponse::de);
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

    private Trilha buscarEntidadeNoTenant(UUID trilhaId, UUID tenantId) {
        Trilha trilha = trilhaRepository.findById(trilhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Trilha não encontrada: " + trilhaId));
        if (!trilha.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Trilha não encontrada: " + trilhaId);
        }
        return trilha;
    }
}
