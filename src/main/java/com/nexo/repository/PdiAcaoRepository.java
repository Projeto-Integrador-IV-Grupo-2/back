package com.nexo.repository;

import com.nexo.entity.PdiAcao;
import com.nexo.entity.StatusAcaoPdi;
import com.nexo.entity.TipoAcaoPdi;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PdiAcaoRepository extends JpaRepository<PdiAcao, UUID> {

    @Query("select a from PdiAcao a where a.pdiItem.pdi.id = :pdiId")
    List<PdiAcao> findByPdiId(@Param("pdiId") UUID pdiId);

    /**
     * Os enums vão como parâmetro, não como literal no JPQL: com o tipo
     * {@code NAMED_ENUM} do Postgres, o Hibernate gera um cast para um tipo com
     * o nome da classe Java ({@code StatusAcaoPdi}), que não existe no banco.
     */
    default List<PdiAcao> acoesDeTrilhaEmAberto(UUID tenantId, UUID trilhaId, UUID colaboradorId) {
        return acoesEmAberto(tenantId, TipoAcaoPdi.TRILHA, trilhaId, colaboradorId, StatusAcaoPdi.CONCLUIDA);
    }

    default List<TotaisPorPdi> totaisPorPdi(Collection<UUID> pdiIds) {
        return totaisPorPdi(pdiIds, StatusAcaoPdi.CONCLUIDA);
    }

    /**
     * Ações TRILHA ainda não concluídas que ligam esta trilha ao PDI deste
     * colaborador — é o que a conclusão da trilha precisa fechar.
     */
    @Query("select a from PdiAcao a "
            + "where a.tenant.id = :tenantId "
            + "and a.tipo = :tipo "
            + "and a.trilha.id = :trilhaId "
            + "and a.pdiItem.pdi.colaborador.id = :colaboradorId "
            + "and a.status <> :concluida")
    List<PdiAcao> acoesEmAberto(@Param("tenantId") UUID tenantId,
                                @Param("tipo") TipoAcaoPdi tipo,
                                @Param("trilhaId") UUID trilhaId,
                                @Param("colaboradorId") UUID colaboradorId,
                                @Param("concluida") StatusAcaoPdi concluida);

    /**
     * Total de ações e quantas estão concluídas, por PDI, numa consulta só
     * (a listagem não faz uma ida ao banco por PDI). PDI sem ação não aparece.
     */
    @Query("select a.pdiItem.pdi.id as pdiId, count(a) as total, "
            + "sum(case when a.status = :concluida then 1 else 0 end) as concluidas "
            + "from PdiAcao a where a.pdiItem.pdi.id in :pdiIds group by a.pdiItem.pdi.id")
    List<TotaisPorPdi> totaisPorPdi(@Param("pdiIds") Collection<UUID> pdiIds,
                                    @Param("concluida") StatusAcaoPdi concluida);

    interface TotaisPorPdi {
        UUID getPdiId();

        Long getTotal();

        Long getConcluidas();
    }
}
