package com.nexo.service;

import com.nexo.dto.DashboardColaboradorResponse;
import com.nexo.dto.ProximoPrazoDashboardResponse;
import com.nexo.dto.ResumoStatusDashboard;
import com.nexo.dto.trilha.ProgressoTrilhaResponse;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta os indicadores pessoais usando sempre o tenant e o usuário autenticados. */
@Service
public class DashboardColaboradorService {

    private final TrilhaAtribuicaoRepository atribuicaoRepository;
    private final RegraDeAtraso regraDeAtraso;
    private final HistoricoInteracaoService historicoService;

    public DashboardColaboradorService(TrilhaAtribuicaoRepository atribuicaoRepository,
                                       RegraDeAtraso regraDeAtraso,
                                       HistoricoInteracaoService historicoService) {
        this.atribuicaoRepository = atribuicaoRepository;
        this.regraDeAtraso = regraDeAtraso;
        this.historicoService = historicoService;
    }

    @Transactional(readOnly = true)
    public DashboardColaboradorResponse gerar(UsuarioAutenticado requisitante) {
        List<TrilhaAtribuicao> atribuicoes = atribuicaoRepository.findByTenantIdAndUsuarioId(
                requisitante.getTenantId(), requisitante.getId());

        List<StatusAtribuicao> statusEfetivos = atribuicoes.stream()
                .map(atribuicao -> regraDeAtraso.statusEfetivo(atribuicao.getStatus(), atribuicao.getPrazo()))
                .toList();
        ResumoStatusDashboard resumo = ResumoStatusDashboard.de(statusEfetivos);

        LocalDate hoje = regraDeAtraso.hoje();
        List<ProximoPrazoDashboardResponse> prazos = atribuicaoRepository
                .findByTenantIdAndUsuarioIdAndPrazoBetweenAndStatusNotOrderByPrazoAsc(
                        requisitante.getTenantId(), requisitante.getId(), hoje, hoje.plusDays(7),
                        StatusAtribuicao.CONCLUIDA)
                .stream()
                .map(atribuicao -> {
                    StatusAtribuicao status = regraDeAtraso.statusEfetivo(
                            atribuicao.getStatus(), atribuicao.getPrazo());
                    return new ProximoPrazoDashboardResponse(
                            atribuicao.getId(),
                            atribuicao.getTrilha().getId(),
                            atribuicao.getTrilha().getNome(),
                            atribuicao.getPrazo(),
                            status,
                            ProgressoTrilhaResponse.percentualDe(atribuicao));
                })
                .toList();

        double media = atribuicoes.stream()
                .mapToInt(ProgressoTrilhaResponse::percentualDe)
                .average()
                .orElse(0.0);
        BigDecimal percentualMedio = BigDecimal.valueOf(media).setScale(2, RoundingMode.HALF_UP);

        return new DashboardColaboradorResponse(
                resumo,
                prazos,
                percentualMedio,
                historicoService.ultimasAtividades(requisitante));
    }

}
