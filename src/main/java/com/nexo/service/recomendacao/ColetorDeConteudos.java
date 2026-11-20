package com.nexo.service.recomendacao;

import com.nexo.entity.StatusConteudo;
import com.nexo.repository.ConteudoRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lê, numa <strong>transação curta</strong>, os conteúdos publicados do tenant
 * que a IA pode usar como etapas ao montar uma trilha.
 *
 * <p>Só conteúdo publicado entra: é o mesmo critério do
 * {@code POST /api/trilhas/{id}/etapas}, então toda etapa sugerida pode ser
 * criada de verdade. A lista é limitada para o prompt não crescer sem teto.</p>
 *
 * <p>Singleton sem estado.</p>
 */
@Component
public class ColetorDeConteudos {

    /** Teto de conteúdos enviados ao modelo. */
    static final int MAXIMO = 80;

    private final ConteudoRepository conteudoRepository;

    public ColetorDeConteudos(ConteudoRepository conteudoRepository) {
        this.conteudoRepository = conteudoRepository;
    }

    @Transactional(readOnly = true)
    public List<ConteudoCandidato> publicados(UUID tenantId) {
        return conteudoRepository
                .findByTenantIdAndStatusOrderByTituloAsc(tenantId, StatusConteudo.PUBLICADO, PageRequest.of(0, MAXIMO))
                .stream()
                .filter(conteudo -> !conteudo.isApenasAdmin())
                .filter(conteudo -> conteudo.getCatalogo() == com.nexo.entity.CatalogoConteudo.TRILHA)
                .map(ConteudoCandidato::de)
                .toList();
    }
}
