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
     *
     * <p>O status vai como parâmetro: um literal de enum no JPQL vira
     * {@code 'FALHOU'::StatusIndexacao} no SQL, e o tipo do PostgreSQL se chama
     * {@code status_indexacao} — a consulta quebrava a cada execução da rotina.</p>
     */
    default List<UUID> idsDeFalhasAbandonadasDesde(OffsetDateTime limite, Pageable pagina) {
        return idsComStatusSemProximaTentativaDesde(StatusIndexacao.FALHOU, limite, pagina);
    }

    @Query("""
            select i.conteudoId from IndexacaoConteudo i
            where i.status = :status
              and i.proximaTentativaEm is null
              and i.atualizadoEm <= :limite
            order by i.atualizadoEm
            """)
    List<UUID> idsComStatusSemProximaTentativaDesde(@Param("status") StatusIndexacao status,
                                                    @Param("limite") OffsetDateTime limite, Pageable pagina);
}
