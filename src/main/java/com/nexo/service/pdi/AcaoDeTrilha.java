package com.nexo.service.pdi;

import com.nexo.dto.pdi.PdiAcaoRequest;
import com.nexo.entity.PdiAcao;
import com.nexo.entity.PdiItem;
import com.nexo.entity.StatusAcaoPdi;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.TipoAcaoPdi;
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
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Ação {@code TRILHA}: além de criar a ação, garante que a trilha esteja
 * atribuída ao colaborador do PDI, com o prazo da ação.
 *
 * <p><strong>Sem duplicar:</strong> se o colaborador já tem atribuição nessa
 * trilha (começou sozinho ou veio de outra ação), ela é reaproveitada como
 * está — não muda prazo nem status de um progresso em curso. Se essa
 * atribuição já está concluída, a ação já nasce concluída: o colaborador não
 * precisa refazer a trilha para o PDI reconhecê-la.</p>
 */
@Component
public class AcaoDeTrilha implements CriadorDeAcao {

    private final TrilhaRepository trilhaRepository;
    private final TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;
    private final UsuarioRepository usuarioRepository;

    public AcaoDeTrilha(TrilhaRepository trilhaRepository,
                        TrilhaAtribuicaoRepository trilhaAtribuicaoRepository,
                        UsuarioRepository usuarioRepository) {
        this.trilhaRepository = trilhaRepository;
        this.trilhaAtribuicaoRepository = trilhaAtribuicaoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Set<TipoAcaoPdi> tipos() {
        return EnumSet.of(TipoAcaoPdi.TRILHA);
    }

    @Override
    public PdiAcao criar(PdiItem item, PdiAcaoRequest request, UsuarioAutenticado requisitante) {
        Trilha trilha = buscarTrilhaAtivaNoTenant(request, requisitante);
        Usuario colaborador = item.getPdi().getColaborador();

        TrilhaAtribuicao atribuicao = trilhaAtribuicaoRepository
                .findByTrilhaIdAndUsuarioId(trilha.getId(), colaborador.getId())
                .orElseGet(() -> atribuir(trilha, colaborador, request, requisitante));

        PdiAcao acao = PdiAcao.deTrilha(item, trilha, request.prazo(), textoOuNulo(request.descricao()));
        if (atribuicao.getStatus() == StatusAtribuicao.CONCLUIDA) {
            OffsetDateTime concluidoEm = atribuicao.getConcluidoEm() != null
                    ? atribuicao.getConcluidoEm()
                    : OffsetDateTime.now();
            acao.mudarStatus(StatusAcaoPdi.CONCLUIDA, concluidoEm);
        }
        return acao;
    }

    private TrilhaAtribuicao atribuir(Trilha trilha, Usuario colaborador, PdiAcaoRequest request,
                                      UsuarioAutenticado requisitante) {
        TrilhaAtribuicao atribuicao = new TrilhaAtribuicao();
        atribuicao.setTenant(trilha.getTenant());
        atribuicao.setTrilha(trilha);
        atribuicao.setUsuario(colaborador);
        atribuicao.setAtribuidoPor(usuarioRepository.getReferenceById(requisitante.getId()));
        atribuicao.setPrazo(request.prazo());
        // Atribuída pelo gestor: o colaborador ainda não começou.
        atribuicao.setStatus(StatusAtribuicao.NAO_INICIADA);
        return trilhaAtribuicaoRepository.saveAndFlush(atribuicao);
    }

    private Trilha buscarTrilhaAtivaNoTenant(PdiAcaoRequest request, UsuarioAutenticado requisitante) {
        Trilha trilha = trilhaRepository.findById(request.trilhaId())
                .filter(t -> t.getTenant().getId().equals(requisitante.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Trilha não encontrada: " + request.trilhaId()));
        if (!trilha.isAtivo()) {
            throw new OperacaoNaoPermitidaException("Trilha inativa não pode ser ligada ao PDI: " + trilha.getNome());
        }
        return trilha;
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
