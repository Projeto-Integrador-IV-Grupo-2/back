package com.nexo.service;

import com.nexo.entity.ChatSessao;
import com.nexo.entity.HistoricoInteracao;
import com.nexo.entity.Tenant;
import com.nexo.entity.TipoInteracaoHistorico;
import com.nexo.entity.Usuario;
import com.nexo.event.PerguntaRespondidaEvent;
import com.nexo.event.SenhaRedefinidaEvent;
import com.nexo.event.SenhaTrocadaEvent;
import com.nexo.event.TrilhaAcessadaEvent;
import com.nexo.event.TrilhaConcluidaEvent;
import com.nexo.event.TrilhaIniciadaEvent;
import com.nexo.repository.ChatSessaoRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.TrilhaRepository;
import com.nexo.repository.UsuarioRepository;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Converte eventos de domínio em registros persistíveis de histórico. */
@Component
public class HistoricoInteracaoFactory {

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final TrilhaRepository trilhaRepository;
    private final ChatSessaoRepository chatSessaoRepository;

    public HistoricoInteracaoFactory(TenantRepository tenantRepository, UsuarioRepository usuarioRepository,
                                     TrilhaRepository trilhaRepository,
                                     ChatSessaoRepository chatSessaoRepository) {
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.trilhaRepository = trilhaRepository;
        this.chatSessaoRepository = chatSessaoRepository;
    }

    public HistoricoInteracao para(TrilhaAcessadaEvent evento) {
        return criar(evento.trilhaId(), evento.trilhaNome(), evento.tenantId(), evento.usuarioId(),
                TipoInteracaoHistorico.TRILHA_ACESSADA, "GET /api/trilhas/{id}");
    }

    public HistoricoInteracao para(TrilhaIniciadaEvent evento) {
        return criar(evento.trilhaId(), evento.trilhaNome(), evento.tenantId(), evento.usuarioId(),
                TipoInteracaoHistorico.TRILHA_INICIADA, "progresso alterado para EM_ANDAMENTO");
    }

    public HistoricoInteracao para(TrilhaConcluidaEvent evento) {
        String trilhaNome = trilhaRepository.findByIdAndTenantId(evento.trilhaId(), evento.tenantId())
                .map(com.nexo.entity.Trilha::getNome)
                .orElse("Trilha removida");
        return criar(evento.trilhaId(), trilhaNome, evento.tenantId(), evento.usuarioId(),
                TipoInteracaoHistorico.TRILHA_CONCLUIDA, "progresso da trilha atualizado para CONCLUIDA");
    }

    /**
     * Consulta ao assistente (issue #59). Guarda a sessão em {@code sessao_id}
     * — a coluna que a V18 deixou adiada até a tabela existir — e, nos
     * detalhes, só o que é seguro auditar: nunca a pergunta nem a resposta.
     */
    public HistoricoInteracao para(PerguntaRespondidaEvent evento) {
        HistoricoInteracao historico = new HistoricoInteracao();
        historico.setTenant(tenantRepository.getReferenceById(evento.tenantId()));
        historico.setUsuario(usuarioRepository.getReferenceById(evento.usuarioId()));
        historico.setSessao(chatSessaoRepository.getReferenceById(evento.sessaoId()));
        historico.setTipo(TipoInteracaoHistorico.ASSISTENTE_CONSULTADO);
        historico.setDetalhes(Map.of(
                "mensagemId", evento.mensagemId().toString(),
                "encontrouFontes", evento.encontrouFontes(),
                "quantidadeFontes", evento.quantidadeFontes(),
                "origem", "POST /api/assistente/sessoes/{id}/mensagens"));
        return historico;
    }

    /**
     * Senha redefinida pelo link do e-mail (issue #76). Os detalhes guardam só
     * a origem — nunca o token nem a senha.
     */
    public HistoricoInteracao para(SenhaRedefinidaEvent evento) {
        HistoricoInteracao historico = new HistoricoInteracao();
        historico.setTenant(tenantRepository.getReferenceById(evento.tenantId()));
        historico.setUsuario(usuarioRepository.getReferenceById(evento.usuarioId()));
        historico.setTipo(TipoInteracaoHistorico.SENHA_REDEFINIDA);
        historico.setDetalhes(Map.of("origem", "POST /api/auth/redefinir-senha"));
        return historico;
    }

    /**
     * Senha trocada pela tela de perfil. Mesmo tipo da redefinição pelo link:
     * para a auditoria, nos dois casos a senha mudou. A origem diferencia.
     */
    public HistoricoInteracao para(SenhaTrocadaEvent evento) {
        HistoricoInteracao historico = new HistoricoInteracao();
        historico.setTenant(tenantRepository.getReferenceById(evento.tenantId()));
        historico.setUsuario(usuarioRepository.getReferenceById(evento.usuarioId()));
        historico.setTipo(TipoInteracaoHistorico.SENHA_REDEFINIDA);
        historico.setDetalhes(Map.of("origem", "PATCH /api/auth/me/senha"));
        return historico;
    }

    private HistoricoInteracao criar(java.util.UUID trilhaId, String trilhaNome,
                                     java.util.UUID tenantId, java.util.UUID usuarioId,
                                     TipoInteracaoHistorico tipo, String origem) {
        Tenant tenant = tenantRepository.getReferenceById(tenantId);
        Usuario usuario = usuarioRepository.getReferenceById(usuarioId);

        HistoricoInteracao historico = new HistoricoInteracao();
        historico.setTenant(tenant);
        historico.setUsuario(usuario);
        historico.setTipo(tipo);
        historico.setDetalhes(Map.of(
                "trilhaId", trilhaId.toString(),
                "trilhaNome", trilhaNome,
                "origem", origem));
        return historico;
    }
}
