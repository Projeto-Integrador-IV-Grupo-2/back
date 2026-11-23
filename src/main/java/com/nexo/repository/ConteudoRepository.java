package com.nexo.repository;

import com.nexo.entity.Conteudo;
import com.nexo.entity.StatusConteudo;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConteudoRepository extends JpaRepository<Conteudo, UUID>, JpaSpecificationExecutor<Conteudo> {

    long countByAreaId(UUID areaId);

    /** Conteúdo do tenant; de outro tenant volta vazio (a API responde 404). */
    Optional<Conteudo> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Conteúdos de um tenant numa situação, em ordem de título — o catálogo que
     * a IA usa para montar as etapas de uma trilha. A página limita o tamanho
     * do prompt.
     */
    List<Conteudo> findByTenantIdAndStatusOrderByTituloAsc(UUID tenantId, StatusConteudo status,
                                                           Pageable pagina);

    /**
     * Conteúdos publicados que a indexação ainda não registrou — publicados
     * antes de a IA ser configurada, ou cujo evento de publicação se perdeu
     * (API reiniciada no meio). {@code ate} dá uma folga para o indexador que
     * roda logo após a publicação terminar primeiro.
     *
     * <p>O status vai como parâmetro: um literal de enum no JPQL vira
     * {@code 'PUBLICADO'::StatusConteudo} no SQL, mas o tipo do PostgreSQL se
     * chama {@code status_conteudo}.</p>
     */
    default List<UUID> idsPublicadosSemIndexacao(OffsetDateTime ate, Pageable pagina) {
        return idsComStatusSemIndexacao(StatusConteudo.PUBLICADO, ate, pagina);
    }

    @Query("""
            select c.id from Conteudo c
            where c.status = :status
              and c.atualizadoEm <= :ate
              and not exists (select 1 from IndexacaoConteudo i where i.conteudoId = c.id)
            order by c.atualizadoEm
            """)
    List<UUID> idsComStatusSemIndexacao(@Param("status") StatusConteudo status, @Param("ate") OffsetDateTime ate,
                                        Pageable pagina);

    /** Contagem de conteúdos por área para uma página inteira de áreas, numa consulta só. */
    @Query("""
            select c.area.id as areaId, count(c) as total
            from Conteudo c
            where c.area.id in :areaIds
            group by c.area.id
            """)
    List<UsuarioRepository.TotalPorArea> contarPorArea(@Param("areaIds") Collection<UUID> areaIds);
}
