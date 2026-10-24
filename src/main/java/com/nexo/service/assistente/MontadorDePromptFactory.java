package com.nexo.service.assistente;

import com.nexo.entity.ContextoChat;
import com.nexo.exception.OperacaoNaoPermitidaException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Factory Method do prompt: entrega o {@link MontadorDePrompt} conforme o
 * contexto da sessão ({@code contexto_chat}). Um contexto novo é só um novo
 * {@code @Component}.
 */
@Component
public class MontadorDePromptFactory {

    private final Map<ContextoChat, MontadorDePrompt> porContexto = new EnumMap<>(ContextoChat.class);

    public MontadorDePromptFactory(List<MontadorDePrompt> montadores) {
        montadores.forEach(montador -> porContexto.put(montador.contexto(), montador));
    }

    /** @throws OperacaoNaoPermitidaException contexto que ainda não aceita perguntas livres (409) */
    public MontadorDePrompt para(ContextoChat contexto) {
        MontadorDePrompt montador = porContexto.get(contexto);
        if (montador == null) {
            throw new OperacaoNaoPermitidaException(
                    "Esta sessão é do contexto " + contexto + ", que não aceita perguntas sobre documentos");
        }
        return montador;
    }
}
