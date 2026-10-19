package com.nexo.service;

import com.nexo.dto.AreaAtrasoDashboardResponse;
import com.nexo.dto.DashboardGestaoResponse;
import com.nexo.dto.ResumoStatusDashboard;
import com.nexo.dto.TrilhaMaisIniciadaDashboardResponse;
import com.nexo.entity.StatusAtribuicao;
import com.nexo.repository.AtribuicaoDashboardProjection;
import com.nexo.repository.HistoricoInteracaoRepository;
import com.nexo.repository.TrilhaAtribuicaoRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Indicadores agregados de gestão; cada consulta recebe explicitamente o tenant autenticado. */
@Service
public class DashboardGestaoService {

    private static final int LIMITE_LISTAS = 5;
    /** Lote para que o dashboard não mantenha todas as atribuições do tenant na memória. */
    private static final int TAMANHO_LOTE = 500;

    private final TrilhaRepository trilhaRepository;
    private final TrilhaAtribuicaoRepository atribuicaoRepository;
    private final HistoricoInteracaoRepository historicoRepository;
    private final RegraDeAtraso regraDeAtraso;

    public DashboardGestaoService(TrilhaRepository trilhaRepository,
                                  TrilhaAtribuicaoRepository atribuicaoRepository,
                                  HistoricoInteracaoRepository historicoRepository,
                                  RegraDeAtraso regraDeAtraso) {
        this.trilhaRepository = trilhaRepository;
        this.atribuicaoRepository = atribuicaoRepository;
        this.historicoRepository = historicoRepository;
        this.regraDeAtraso = regraDeAtraso;
    }

    @Transactional(readOnly = true)
    public DashboardGestaoResponse gerar(UsuarioAutenticado requisitante) {
        UUID tenantId = requisitante.getTenantId();
        long trilhasAtivas = trilhaRepository.countByTenantIdAndAtivoTrue(tenantId);
        AgregadoAtribuicoes agregado = agregarAtribuicoes(tenantId);
        ResumoStatusDashboard status = agregado.status();
        BigDecimal taxaConclusao = status.total() == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(status.concluidas())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(status.total()), 2, RoundingMode.HALF_UP);

        List<AreaAtrasoDashboardResponse> areasAtrasadas = agregado.areasComAtraso();

        List<TrilhaMaisIniciadaDashboardResponse> trilhasIniciadas = historicoRepository
                .encontrarTrilhasMaisIniciadas(tenantId, LIMITE_LISTAS)
                .stream()
                .map(this::paraTrilhaMaisIniciada)
                .toList();

        return new DashboardGestaoResponse(
                trilhasAtivas, status, taxaConclusao, areasAtrasadas, trilhasIniciadas);
    }

    /**
     * Varre as atribuições em lotes estáveis, mantendo em Java a regra de atraso
     * e sem materializar todos os registros do tenant ao mesmo tempo.
     */
    private AgregadoAtribuicoes agregarAtribuicoes(UUID tenantId) {
        Map<StatusAtribuicao, Long> contagemStatus = new EnumMap<>(StatusAtribuicao.class);
        Map<UUID, AreaContagem> contagensArea = new HashMap<>();
        long total = 0;
        Pageable pageable = PageRequest.of(0, TAMANHO_LOTE);
        Slice<AtribuicaoDashboardProjection> lote;
        do {
            lote = atribuicaoRepository.buscarDadosDashboardPorTenant(tenantId, pageable);
            for (AtribuicaoDashboardProjection atribuicao : lote.getContent()) {
                StatusAtribuicao efetivo = regraDeAtraso.statusEfetivo(
                        atribuicao.getStatus(), atribuicao.getPrazo());
                contagemStatus.merge(efetivo, 1L, Long::sum);
                total++;

                UUID areaId = atribuicao.getAreaId();
                if (efetivo == StatusAtribuicao.ATRASADA && areaId != null) {
                    contagensArea.compute(areaId, (id, existente) -> existente == null
                            ? new AreaContagem(id, atribuicao.getAreaNome(), 1)
                            : new AreaContagem(id, existente.nome(), existente.quantidade() + 1));
                }
            }
            if (lote.hasNext()) {
                pageable = lote.nextPageable();
            }
        } while (lote.hasNext());

        ResumoStatusDashboard status = new ResumoStatusDashboard(
                total,
                contagemStatus.getOrDefault(StatusAtribuicao.NAO_INICIADA, 0L),
                contagemStatus.getOrDefault(StatusAtribuicao.EM_ANDAMENTO, 0L),
                contagemStatus.getOrDefault(StatusAtribuicao.CONCLUIDA, 0L),
                contagemStatus.getOrDefault(StatusAtribuicao.ATRASADA, 0L));

        List<AreaAtrasoDashboardResponse> areas = contagensArea.values().stream()
                .sorted(Comparator.comparingLong(AreaContagem::quantidade).reversed()
                        .thenComparing(AreaContagem::nome, Comparator.nullsLast(String::compareTo)))
                .limit(LIMITE_LISTAS)
                .map(area -> new AreaAtrasoDashboardResponse(area.id(), area.nome(), area.quantidade()))
                .toList();
        return new AgregadoAtribuicoes(status, areas);
    }

    private TrilhaMaisIniciadaDashboardResponse paraTrilhaMaisIniciada(Object[] linha) {
        UUID trilhaId = UUID.fromString((String) linha[0]);
        String trilhaNome = (String) linha[1];
        long total = ((Number) linha[2]).longValue();
        return new TrilhaMaisIniciadaDashboardResponse(trilhaId, trilhaNome, total);
    }

    private record AreaContagem(UUID id, String nome, long quantidade) {
    }

    private record AgregadoAtribuicoes(
            ResumoStatusDashboard status, List<AreaAtrasoDashboardResponse> areasComAtraso) {
    }
}
