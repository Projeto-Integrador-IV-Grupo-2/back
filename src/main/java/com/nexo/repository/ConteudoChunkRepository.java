package com.nexo.repository;

import com.nexo.entity.ConteudoChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConteudoChunkRepository extends JpaRepository<ConteudoChunk, UUID> {

    long countByConteudoId(UUID conteudoId);

    List<ConteudoChunk> findByConteudoIdOrderByOrdem(UUID conteudoId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ConteudoChunk c where c.conteudoId = :conteudoId")
    int apagarDoConteudo(@Param("conteudoId") UUID conteudoId);

    /**
     * Trechos mais parecidos com o vetor, por distância de cosseno.
     *
     * <p>Só do tenant e só de conteúdo publicado — o filtro está na própria
     * consulta, então nada de outra empresa ou de rascunho sai do banco.
     * Conteúdo restrito à gestão só entra com {@code incluirRestritos}.</p>
     *
     * @param vetor literal do pgvector, ex.: {@code [0.1,0.2,...]}
     */
    @Query(value = """
            select ch.id as chunkId,
                   ch.conteudo_id as conteudoId,
                   c.titulo as titulo,
                   ch.ordem as ordem,
                   ch.texto as texto,
                   ch.embedding <=> cast(:vetor as vector) as distancia
            from conteudo_chunk ch
            join conteudo c on c.id = ch.conteudo_id
            where ch.tenant_id = :tenantId
              and c.tenant_id = :tenantId
              and c.status = 'PUBLICADO'
              and (c.apenas_admin = false or :incluirRestritos)
            order by ch.embedding <=> cast(:vetor as vector)
            limit :limite
            """, nativeQuery = true)
    List<TrechoProjecao> buscarParecidos(@Param("tenantId") UUID tenantId,
                                         @Param("vetor") String vetor,
                                         @Param("limite") int limite,
                                         @Param("incluirRestritos") boolean incluirRestritos);

    /** Sem os restritos à gestão: o padrão seguro. */
    default List<TrechoProjecao> buscarParecidos(UUID tenantId, String vetor, int limite) {
        return buscarParecidos(tenantId, vetor, limite, false);
    }

    /**
     * Trechos que contêm palavras da pergunta (busca textual do PostgreSQL,
     * dicionário {@code portuguese}, com radicais: "funcionários" casa com
     * "funcionário"). Complementa a busca por vetor, que às vezes deixa de fora
     * o trecho exato quando vários trechos falam da mesma empresa.
     *
     * <p>Mesmos filtros de tenant, publicado e restrito da busca por vetor. A
     * distância de cosseno também volta, só para informação.</p>
     *
     * @param consulta termos no formato do {@code to_tsquery}, ex.: {@code funcionarios | sede}
     */
    @Query(value = """
            select ch.id as chunkId,
                   ch.conteudo_id as conteudoId,
                   c.titulo as titulo,
                   ch.ordem as ordem,
                   ch.texto as texto,
                   ch.embedding <=> cast(:vetor as vector) as distancia
            from conteudo_chunk ch
            join conteudo c on c.id = ch.conteudo_id
            where ch.tenant_id = :tenantId
              and c.tenant_id = :tenantId
              and c.status = 'PUBLICADO'
              and (c.apenas_admin = false or :incluirRestritos)
              and to_tsvector('portuguese', ch.texto) @@ to_tsquery('portuguese', :consulta)
            order by ts_rank_cd(to_tsvector('portuguese', ch.texto), to_tsquery('portuguese', :consulta)) desc
            limit :limite
            """, nativeQuery = true)
    List<TrechoProjecao> buscarPorPalavras(@Param("tenantId") UUID tenantId,
                                           @Param("consulta") String consulta,
                                           @Param("vetor") String vetor,
                                           @Param("limite") int limite,
                                           @Param("incluirRestritos") boolean incluirRestritos);

    interface TrechoProjecao {
        UUID getChunkId();

        UUID getConteudoId();

        String getTitulo();

        Integer getOrdem();

        String getTexto();

        Double getDistancia();
    }
}
