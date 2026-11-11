package com.nexo.service.notificacao;

import com.nexo.dto.notificacao.NotificacaoResponse;
import com.nexo.entity.Notificacao;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.NotificacaoRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura das notificações do usuário logado (issue #78).
 *
 * <p><strong>Sempre do dono:</strong> nenhuma rota aceita o id de outro
 * usuário — o dono é o do token. Notificação de outra pessoa ou de outro
 * tenant responde 404 (nunca 403), como se não existisse.</p>
 *
 * <p>Singleton do Spring e sem estado.</p>
 */
@Service
public class NotificacaoService {

    private static final int TAMANHO_MAXIMO_PAGINA = 50;

    private final NotificacaoRepository notificacaoRepository;
    private final Clock clock;

    public NotificacaoService(NotificacaoRepository notificacaoRepository, Clock clock) {
        this.notificacaoRepository = notificacaoRepository;
        this.clock = clock;
    }

    /** Mais recentes primeiro; {@code naoLidas} filtra só as não lidas. A ordem é fixa. */
    @Transactional(readOnly = true)
    public Page<NotificacaoResponse> listar(boolean naoLidas, Pageable pageable, UsuarioAutenticado usuario) {
        Pageable pagina = PageRequest.of(Math.max(0, pageable.getPageNumber()),
                Math.max(1, Math.min(pageable.getPageSize(), TAMANHO_MAXIMO_PAGINA)));
        Page<Notificacao> resultado = naoLidas
                ? notificacaoRepository.findByTenantIdAndUsuarioIdAndLidaEmIsNullOrderByCriadaEmDescIdDesc(
                        usuario.getTenantId(), usuario.getId(), pagina)
                : notificacaoRepository.findByTenantIdAndUsuarioIdOrderByCriadaEmDescIdDesc(
                        usuario.getTenantId(), usuario.getId(), pagina);
        return resultado.map(NotificacaoResponse::de);
    }

    @Transactional(readOnly = true)
    public long totalNaoLidas(UsuarioAutenticado usuario) {
        return notificacaoRepository.countByTenantIdAndUsuarioIdAndLidaEmIsNull(usuario.getTenantId(), usuario.getId());
    }

    /** Marca como lida. Já lida, continua lida (idempotente). */
    @Transactional
    public NotificacaoResponse marcarLida(UUID id, UsuarioAutenticado usuario) {
        Notificacao notificacao = notificacaoRepository
                .findByIdAndTenantIdAndUsuarioId(id, usuario.getTenantId(), usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notificação não encontrada: " + id));
        notificacao.marcarLida(OffsetDateTime.now(clock));
        return NotificacaoResponse.de(notificacaoRepository.saveAndFlush(notificacao));
    }

    /** Marca todas as não lidas do usuário como lidas e devolve quantas eram. */
    @Transactional
    public int marcarTodasLidas(UsuarioAutenticado usuario) {
        return notificacaoRepository.marcarTodasComoLidas(usuario.getTenantId(), usuario.getId(),
                OffsetDateTime.now(clock));
    }
}
