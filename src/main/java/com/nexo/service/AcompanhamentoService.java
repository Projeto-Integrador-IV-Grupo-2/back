package com.nexo.service;

import com.nexo.dto.atribuicao.AtribuicaoAcompanhamentoResponse;
import com.nexo.dto.atribuicao.ColaboradorAcompanhamentoResponse;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AtribuicaoFiltro;
import com.nexo.repository.AtribuicaoSpecifications;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.exportacao.ArquivoExportado;
import com.nexo.service.exportacao.ExportadorFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acompanhamento das trilhas pelo gestor e pelo admin: quem está com qual
 * trilha, em que status e com que prazo.
 *
 * <p>Só leitura. O escopo é sempre o tenant do usuário autenticado, e o status
 * devolvido é o efetivo ({@link RegraDeAtraso}): prazo vencido e não concluída
 * aparece como ATRASADA mesmo antes de a situação ser gravada no banco.</p>
 */
@Service
public class AcompanhamentoService {

    private static final Sort ORDEM_DO_COLABORADOR = Sort.by(Sort.Direction.DESC, "atribuidoEm");

    private final TrilhaAtribuicaoRepository trilhaAtribuicaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RegraDeAtraso regraDeAtraso;
    private final ExportadorFactory exportadores;

    public AcompanhamentoService(TrilhaAtribuicaoRepository trilhaAtribuicaoRepository,
                                 UsuarioRepository usuarioRepository,
                                 RegraDeAtraso regraDeAtraso,
                                 ExportadorFactory exportadores) {
        this.trilhaAtribuicaoRepository = trilhaAtribuicaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.regraDeAtraso = regraDeAtraso;
        this.exportadores = exportadores;
    }

    @Transactional(readOnly = true)
    public Page<AtribuicaoAcompanhamentoResponse> listar(AtribuicaoFiltro filtro, Pageable pageable,
                                                         UsuarioAutenticado requisitante) {
        return trilhaAtribuicaoRepository.findAll(especificacao(filtro, requisitante), pageable)
                .map(this::paraResposta);
    }

    /** Todas as trilhas de um colaborador do tenant, com os totais por status. */
    @Transactional(readOnly = true)
    public ColaboradorAcompanhamentoResponse colaborador(UUID usuarioId, UsuarioAutenticado requisitante) {
        Usuario colaborador = usuarioRepository.findById(usuarioId)
                .filter(usuario -> usuario.getTenant().getId().equals(requisitante.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + usuarioId));

        List<AtribuicaoAcompanhamentoResponse> trilhas = trilhaAtribuicaoRepository
                .findAll(especificacao(AtribuicaoFiltro.doColaborador(usuarioId), requisitante), ORDEM_DO_COLABORADOR)
                .stream()
                .map(this::paraResposta)
                .toList();
        return ColaboradorAcompanhamentoResponse.de(colaborador, trilhas);
    }

    /** Mesmos filtros e ordenação da listagem, sem paginação, no formato pedido. */
    @Transactional(readOnly = true)
    public ArquivoExportado exportar(AtribuicaoFiltro filtro, Sort sort, String formato,
                                     UsuarioAutenticado requisitante) {
        var exportador = exportadores.para(formato);
        List<AtribuicaoAcompanhamentoResponse> linhas = trilhaAtribuicaoRepository
                .findAll(especificacao(filtro, requisitante), sort)
                .stream()
                .map(this::paraResposta)
                .toList();
        return exportador.exportar(linhas);
    }

    private Specification<TrilhaAtribuicao> especificacao(AtribuicaoFiltro filtro, UsuarioAutenticado requisitante) {
        return AtribuicaoSpecifications.comFiltro(filtro, requisitante.getTenantId(), regraDeAtraso.hoje());
    }

    private AtribuicaoAcompanhamentoResponse paraResposta(TrilhaAtribuicao atribuicao) {
        return AtribuicaoAcompanhamentoResponse.de(
                atribuicao, regraDeAtraso.statusEfetivo(atribuicao.getStatus(), atribuicao.getPrazo()));
    }
}
