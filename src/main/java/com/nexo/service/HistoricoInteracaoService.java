package com.nexo.service;

import com.nexo.dto.HistoricoInteracaoResponse;
import com.nexo.entity.HistoricoInteracao;
import com.nexo.entity.Perfil;
import com.nexo.event.PerguntaRespondidaEvent;
import com.nexo.event.SenhaRedefinidaEvent;
import com.nexo.event.TrilhaAcessadaEvent;
import com.nexo.event.TrilhaConcluidaEvent;
import com.nexo.event.TrilhaIniciadaEvent;
import com.nexo.exception.ParametroInvalidoException;
import com.nexo.repository.HistoricoInteracaoRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persistência isolada e leitura autorizada do histórico de interações. */
@Service
public class HistoricoInteracaoService {

    private static final int TAMANHO_MAXIMO_PAGINA = 100;
    /** Impede consultas de offset profundo que forçariam o banco a percorrer milhões de linhas. */
    private static final long OFFSET_MAXIMO = 100_000L;
    private static final Sort ORDEM_HISTORICO = Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"));

    private final HistoricoInteracaoRepository historicoRepository;
    private final HistoricoInteracaoFactory factory;

    public HistoricoInteracaoService(HistoricoInteracaoRepository historicoRepository,
                                     HistoricoInteracaoFactory factory) {
        this.historicoRepository = historicoRepository;
        this.factory = factory;
    }

    /**
     * Transforma e persiste eventos em uma transação própria. A transação é
     * iniciada pelo proxy Spring e pode falhar sem reverter a operação de trilha.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(TrilhaAcessadaEvent evento) {
        persistir(factory.para(evento));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(TrilhaIniciadaEvent evento) {
        persistir(factory.para(evento));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(TrilhaConcluidaEvent evento) {
        persistir(factory.para(evento));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(PerguntaRespondidaEvent evento) {
        persistir(factory.para(evento));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(SenhaRedefinidaEvent evento) {
        persistir(factory.para(evento));
    }

    @Transactional(readOnly = true)
    public Page<HistoricoInteracaoResponse> listar(Pageable pageable, UsuarioAutenticado requisitante) {
        Pageable paginaSegura = paginaSegura(pageable);
        Page<HistoricoInteracao> resultado;
        if (requisitante.getPapel() == Perfil.ADMIN) {
            resultado = historicoRepository.findAllByTenantId(requisitante.getTenantId(), paginaSegura);
        } else {
            // GESTOR e COLABORADOR consultam somente o próprio histórico.
            resultado = historicoRepository.findAllByTenantIdAndUsuarioId(
                    requisitante.getTenantId(), requisitante.getId(), paginaSegura);
        }
        return resultado.map(HistoricoInteracaoResponse::de);
    }

    @Transactional(readOnly = true)
    public List<HistoricoInteracaoResponse> ultimasAtividades(UsuarioAutenticado requisitante) {
        return historicoRepository.findTop10ByTenantIdAndUsuarioIdOrderByCriadoEmDescIdDesc(
                        requisitante.getTenantId(), requisitante.getId())
                .stream()
                .map(HistoricoInteracaoResponse::de)
                .toList();
    }

    private void persistir(HistoricoInteracao historico) {
        historicoRepository.saveAndFlush(historico);
    }

    private Pageable paginaSegura(Pageable solicitada) {
        int numeroPagina = solicitada == null ? 0 : solicitada.getPageNumber();
        int tamanhoSolicitado = solicitada == null ? 20 : solicitada.getPageSize();
        int pagina = Math.max(0, numeroPagina);
        int tamanhoPagina = Math.max(1, Math.min(tamanhoSolicitado, TAMANHO_MAXIMO_PAGINA));
        if ((long) pagina * tamanhoPagina > OFFSET_MAXIMO) {
            throw new ParametroInvalidoException(
                    "A página solicitada é muito distante; refine a consulta ou use uma página anterior");
        }
        return PageRequest.of(pagina, tamanhoPagina, ORDEM_HISTORICO);
    }
}
