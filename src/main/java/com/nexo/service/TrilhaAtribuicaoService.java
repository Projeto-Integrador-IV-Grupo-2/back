package com.nexo.service;

import com.nexo.dto.trilha.AtualizarProgressoRequest;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio do progresso de um usuário numa trilha (Card 07).
 *
 * <p><strong>Sempre o próprio usuário:</strong> nenhum método aqui aceita um
 * id de usuário vindo do cliente — o dono do progresso é sempre
 * {@link UsuarioAutenticado}, extraído do contexto de segurança pelo
 * controller. Isso já impede, por construção, que alguém altere o progresso
 * de outro usuário: não há como nomear "outro usuário" numa chamada.</p>
 *
 * <p>Acompanhamento de equipe por Gestor/Admin (ver histórico do Card 07) foi
 * avaliado e fica fora deste card: os quatro métodos pedidos (iniciar,
 * atualizar, concluir, buscar) são todos sobre o progresso do próprio
 * requisitante. Uma tela de gestor acompanhando a equipe é uma consulta
 * diferente (lista o progresso de vários usuários), não uma exceção a estas
 * regras — fica para quando essa necessidade de produto existir.</p>
 */
@Service
public class TrilhaAtribuicaoService {

    private final TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;
    private final TrilhaRepository trilhaRepository;
    private final UsuarioRepository usuarioRepository;

    public TrilhaAtribuicaoService(TrilhaAtribuicaoRepository trilhaAtribuicaoRepository,
                                    TrilhaRepository trilhaRepository,
                                    UsuarioRepository usuarioRepository) {
        this.trilhaAtribuicaoRepository = trilhaAtribuicaoRepository;
        this.trilhaRepository = trilhaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Retorna o progresso do requisitante na trilha, criando-o (status
     * {@code EM_ANDAMENTO}) se esta for a primeira interação dele com ela.
     */
    @Transactional
    public ProgressoTrilhaResponse iniciar(UUID trilhaId, UsuarioAutenticado requisitante) {
        Trilha trilha = buscarTrilhaNoTenant(trilhaId, requisitante.getTenantId());
        return trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId())
                .map(ProgressoTrilhaResponse::de)
                .orElseGet(() -> ProgressoTrilhaResponse.de(criarProgresso(trilha, requisitante)));
    }

    /** Atualiza o status do progresso já existente do requisitante na trilha. */
    @Transactional
    public ProgressoTrilhaResponse atualizar(UUID trilhaId, AtualizarProgressoRequest request,
                                              UsuarioAutenticado requisitante) {
        TrilhaAtribuicao progresso = buscarProgressoDoRequisitante(trilhaId, requisitante);
        aplicarStatus(progresso, request.status());
        return ProgressoTrilhaResponse.de(trilhaAtribuicaoRepository.saveAndFlush(progresso));
    }

    /** Marca o progresso do requisitante na trilha como concluído agora. */
    @Transactional
    public ProgressoTrilhaResponse concluir(UUID trilhaId, UsuarioAutenticado requisitante) {
        TrilhaAtribuicao progresso = buscarProgressoDoRequisitante(trilhaId, requisitante);
        aplicarStatus(progresso, StatusAtribuicao.CONCLUIDA);
        return ProgressoTrilhaResponse.de(trilhaAtribuicaoRepository.saveAndFlush(progresso));
    }

    /** Busca o progresso do requisitante na trilha. Nunca cria — use {@link #iniciar} para isso. */
    @Transactional(readOnly = true)
    public ProgressoTrilhaResponse buscarProgresso(UUID trilhaId, UsuarioAutenticado requisitante) {
        return ProgressoTrilhaResponse.de(buscarProgressoDoRequisitante(trilhaId, requisitante));
    }

    private TrilhaAtribuicao criarProgresso(Trilha trilha, UsuarioAutenticado requisitante) {
        Usuario usuarioRef = usuarioRepository.getReferenceById(requisitante.getId());

        TrilhaAtribuicao progresso = new TrilhaAtribuicao();
        progresso.setTenant(trilha.getTenant());
        progresso.setTrilha(trilha);
        progresso.setUsuario(usuarioRef);
        // Sem fluxo de atribuição por Gestor/Admin implementado ainda: o
        // primeiro acesso é sempre auto-iniciado pelo próprio usuário.
        progresso.setAtribuidoPor(usuarioRef);
        progresso.setStatus(StatusAtribuicao.EM_ANDAMENTO);
        return trilhaAtribuicaoRepository.saveAndFlush(progresso);
    }

    private TrilhaAtribuicao buscarProgressoDoRequisitante(UUID trilhaId, UsuarioAutenticado requisitante) {
        // Garante 404 de trilha (não 404 de progresso) quando o id nem é uma trilha válida do tenant.
        buscarTrilhaNoTenant(trilhaId, requisitante.getTenantId());
        return trilhaAtribuicaoRepository.findByTrilhaIdAndUsuarioId(trilhaId, requisitante.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Progresso não encontrado para esta trilha"));
    }

    private Trilha buscarTrilhaNoTenant(UUID trilhaId, UUID tenantId) {
        Trilha trilha = trilhaRepository.findById(trilhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Trilha não encontrada: " + trilhaId));
        if (!trilha.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Trilha não encontrada: " + trilhaId);
        }
        return trilha;
    }

    /**
     * {@code NAO_INICIADA} só existe implicitamente (ausência de registro): uma
     * vez criado o progresso, não faz sentido negociá-lo de volta a esse estado.
     */
    private void aplicarStatus(TrilhaAtribuicao progresso, StatusAtribuicao novoStatus) {
        if (novoStatus == StatusAtribuicao.NAO_INICIADA) {
            throw new OperacaoNaoPermitidaException(
                    "Não é possível voltar um progresso já iniciado para NAO_INICIADA");
        }
        progresso.setStatus(novoStatus);
        // Regressão de status (ex.: reabrir uma trilha concluída) invalida a
        // data de conclusão anterior — senão ficaria "concluída" com data velha.
        progresso.setConcluidoEm(novoStatus == StatusAtribuicao.CONCLUIDA ? OffsetDateTime.now() : null);
    }
}
