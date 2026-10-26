package com.nexo.service.recomendacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.dto.trilha.AjudaTrilhasResponse;
import com.nexo.ia.ClienteIa;
import com.nexo.ia.MensagemIa;
import com.nexo.security.UsuarioAutenticado;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Ajuda da IA na tela de Trilhas: responde pedidos livres sobre trilhas e,
 * quando o pedido é para criar uma, devolve um rascunho pronto para o
 * cadastro.
 *
 * <p>Usa os mesmos dados da sugestão de trilhas — o perfil do usuário e o
 * catálogo de trilhas ativas que ele ainda não concluiu, lidos pelo
 * {@link ColetorDeDadosDaSugestao} —, então a IA só cita trilhas do próprio
 * tenant e nenhum dado pessoal vai para o modelo.</p>
 *
 * <p><strong>Sem {@code @Transactional} de propósito</strong>, pela mesma
 * razão do {@code SugestaoDeTrilhasService}: o banco é lido antes, numa
 * transação curta, e só depois o modelo é consultado.</p>
 *
 * <p>O rascunho nunca é gravado: quem cria a trilha revisa no formulário e
 * salva pelo {@code POST /api/trilhas} de sempre, com as validações dele.
 * Com a IA fora do ar, a {@link com.nexo.exception.IaIndisponivelException}
 * sobe e a API responde 503.</p>
 *
 * <p>Singleton sem estado.</p>
 */
@Service
public class AjudaDeTrilhasService {

    private final ColetorDeDadosDaSugestao coletor;
    private final ColetorDeConteudos coletorDeConteudos;
    private final ClienteIa clienteIa;
    private final ObjectMapper objectMapper;

    public AjudaDeTrilhasService(ColetorDeDadosDaSugestao coletor, ColetorDeConteudos coletorDeConteudos,
                                 ClienteIa clienteIa, ObjectMapper objectMapper) {
        this.coletor = coletor;
        this.coletorDeConteudos = coletorDeConteudos;
        this.clienteIa = clienteIa;
        this.objectMapper = objectMapper;
    }

    public AjudaTrilhasResponse ajudar(String pergunta, UsuarioAutenticado requisitante) {
        // Os dois coletores leem em transações curtas, que fecham antes do modelo.
        DadosParaSugestao dados = coletor.coletar(requisitante);
        List<ConteudoCandidato> conteudos = coletorDeConteudos.publicados(requisitante.getTenantId());
        String turno = PromptDeAjuda.turno(dados.perfil(), dados.catalogo(), conteudos, pergunta);
        String resposta = clienteIa.gerarResposta(PromptDeAjuda.PROMPT_SISTEMA,
                List.of(MensagemIa.doUsuario(turno))).texto();
        return PromptDeAjuda.ler(objectMapper, resposta, dados.catalogo(), conteudos);
    }
}
