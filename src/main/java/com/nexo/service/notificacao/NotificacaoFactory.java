package com.nexo.service.notificacao;

import com.nexo.entity.Conteudo;
import com.nexo.entity.Notificacao;
import com.nexo.entity.TipoConteudo;
import com.nexo.entity.TipoNotificacao;
import com.nexo.entity.Trilha;
import com.nexo.entity.TrilhaAtribuicao;
import com.nexo.entity.Usuario;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Factory Method das notificações (issue #78): cada tipo tem o próprio
 * método, que monta título, mensagem e o link — a rota do front para onde o
 * aviso leva. Quem gera não conhece texto nem rota.
 *
 * <p>Singleton do Spring e sem estado.</p>
 */
@Component
public class NotificacaoFactory {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Rotas do front (front/src/routes/paths.ts). */
    static final String ROTA_TRILHAS = "/trilhas";
    static final String ROTA_ACOMPANHAMENTO = "/acompanhamento";
    static final String ROTA_CONHECIMENTO = "/conhecimento";
    static final String ROTA_CONTEUDOS = "/conteudos";

    public Notificacao trilhaAtribuida(Usuario destinatario, Trilha trilha, LocalDate prazo) {
        String mensagem = "A trilha “" + trilha.getNome() + "” foi atribuída a você"
                + (prazo == null ? "." : ", com prazo até " + prazo.format(DATA) + ".");
        return Notificacao.nova(destinatario, TipoNotificacao.TRILHA_ATRIBUIDA, "Nova trilha para você",
                mensagem, rotaDaTrilha(trilha), trilha.getId());
    }

    public Notificacao prazoProximo(TrilhaAtribuicao atribuicao, LocalDate hoje) {
        Trilha trilha = atribuicao.getTrilha();
        long dias = ChronoUnit.DAYS.between(hoje, atribuicao.getPrazo());
        String quando = dias <= 0 ? "vence hoje"
                : dias == 1 ? "vence amanhã"
                : "vence em " + dias + " dias";
        return Notificacao.nova(atribuicao.getUsuario(), TipoNotificacao.PRAZO_PROXIMO, "Prazo chegando",
                "A trilha “" + trilha.getNome() + "” " + quando + " (" + atribuicao.getPrazo().format(DATA) + ").",
                rotaDaTrilha(trilha), atribuicao.getId());
    }

    public Notificacao prazoVencido(TrilhaAtribuicao atribuicao) {
        Trilha trilha = atribuicao.getTrilha();
        return Notificacao.nova(atribuicao.getUsuario(), TipoNotificacao.PRAZO_VENCIDO, "Prazo vencido",
                "O prazo da trilha “" + trilha.getNome() + "” venceu em "
                        + atribuicao.getPrazo().format(DATA) + ". Conclua assim que puder.",
                rotaDaTrilha(trilha), atribuicao.getId());
    }

    public Notificacao trilhaConcluida(Usuario gestor, Usuario colaborador, Trilha trilha) {
        return Notificacao.nova(gestor, TipoNotificacao.TRILHA_CONCLUIDA, "Trilha concluída na equipe",
                colaborador.getNomeCompleto() + " concluiu a trilha “" + trilha.getNome() + "”.",
                ROTA_ACOMPANHAMENTO, trilha.getId());
    }

    /** Documento abre o detalhe dele em Conhecimento; os outros tipos, o catálogo de conteúdos. */
    public Notificacao conteudoPublicado(Usuario destinatario, Conteudo conteudo) {
        String titulo = conteudo.getArea() == null ? "Novo conteúdo publicado" : "Novo conteúdo na sua área";
        String link = conteudo.getTipo() == TipoConteudo.DOCUMENTO
                ? ROTA_CONHECIMENTO + "/" + conteudo.getId()
                : ROTA_CONTEUDOS;
        return Notificacao.nova(destinatario, TipoNotificacao.CONTEUDO_PUBLICADO, titulo,
                "“" + conteudo.getTitulo() + "” foi publicado.", link, conteudo.getId());
    }

    /** A trilha inativa sai do catálogo: o link leva à lista de trilhas, não ao detalhe. */
    public Notificacao trilhaInativada(Usuario destinatario, Trilha trilha) {
        return Notificacao.nova(destinatario, TipoNotificacao.TRILHA_INATIVADA, "Trilha inativada",
                "A trilha “" + trilha.getNome() + "” foi inativada e não está mais disponível.",
                ROTA_TRILHAS, trilha.getId());
    }

    private static String rotaDaTrilha(Trilha trilha) {
        return ROTA_TRILHAS + "/" + trilha.getId();
    }
}
