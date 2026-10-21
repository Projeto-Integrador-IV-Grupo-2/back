package com.nexo.repository;

import com.nexo.entity.IndexacaoConteudo;
import com.nexo.entity.StatusIndexacao;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexacaoConteudoRepository extends JpaRepository<IndexacaoConteudo, UUID> {

    /** Falhas cuja próxima tentativa já venceu, ainda abaixo do limite de tentativas. */
    List<IndexacaoConteudo> findByStatusAndTentativasLessThanAndProximaTentativaEmLessThanEqual(
            StatusIndexacao status, int maxTentativas, OffsetDateTime agora);
}
