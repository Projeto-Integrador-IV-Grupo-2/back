package com.nexo.service.indexacao;

import com.nexo.entity.Conteudo;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Título, descrição e corpo do conteúdo. Para vídeo e link, que não têm
 * corpo, título e descrição ainda deixam o material encontrável.
 */
@Component
@Order(0)
public class CamposDoConteudo implements FonteDeTextoDoConteudo {

    @Override
    public String textoDe(Conteudo conteudo) {
        return Stream.of(conteudo.getTitulo(), conteudo.getDescricao(), conteudo.getCorpo())
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(texto -> !texto.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }
}
