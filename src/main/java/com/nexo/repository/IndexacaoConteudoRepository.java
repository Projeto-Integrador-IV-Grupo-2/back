package com.nexo.repository;

import com.nexo.entity.IndexacaoConteudo;
import com.nexo.entity.StatusIndexacao;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IndexacaoConteudoRepository extends JpaRepository<IndexacaoConteudo, UUID> {

    /** Falhas cuja próxima tentativa já venceu, ainda abaixo do limite de tentativas. */
    List<IndexacaoConteudo> findByStatusAndTentativasLessThanAndProximaTentativaEmLessThanEqual(
            StatusIndexacao status, int maxTentativas, OffsetDateTime agora);

    /**
     * Falhas que esgotaram as tentativas (sem próxima agendada) e estão paradas
     * desde antes de {@code limite} — ganham uma nova chance de tempos em
     * tempos, para uma queda longa do provedor não tirar o documento da busca
     * para sempre.
     */
    @Query("""
            select i.conteudoId from IndexacaoConteudo i
            where i.status = com.nexo.entity.StatusIndexacao.FALHOU
              and i.proximaTentativaEm is null
              and i.atualizadoEm <= :limite
            order by i.atualizadoEm
            """)
    List<UUID> idsDeFalhasAbandonadasDesde(@Param("limite") OffsetDateTime limite, Pageable pagina);
}
