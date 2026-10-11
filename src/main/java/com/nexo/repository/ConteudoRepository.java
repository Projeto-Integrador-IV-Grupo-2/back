package com.nexo.repository;

import com.nexo.entity.Conteudo;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConteudoRepository extends JpaRepository<Conteudo, UUID>, JpaSpecificationExecutor<Conteudo> {

    long countByAreaId(UUID areaId);

    /** Contagem de conteúdos por área para uma página inteira de áreas, numa consulta só. */
    @Query("""
            select c.area.id as areaId, count(c) as total
            from Conteudo c
            where c.area.id in :areaIds
            group by c.area.id
            """)
    List<UsuarioRepository.TotalPorArea> contarPorArea(@Param("areaIds") Collection<UUID> areaIds);
}
