package com.nexo.controller;

import com.nexo.dto.notificacao.NotificacaoResponse;
import com.nexo.dto.notificacao.TotalNaoLidasResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.notificacao.NotificacaoService;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notificações do usuário logado (issue #78). Qualquer perfil autenticado;
 * cada um só alcança as próprias — quem decide é o {@link NotificacaoService}.
 */
@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    /** Lista paginada, mais recentes primeiro; {@code ?naoLidas=true} filtra. */
    @GetMapping
    public PagedModel<NotificacaoResponse> listar(@RequestParam(defaultValue = "false") boolean naoLidas,
                                                  @PageableDefault(size = 20) Pageable pageable,
                                                  @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new PagedModel<>(notificacaoService.listar(naoLidas, pageable, usuario));
    }

    /** O número do sino. */
    @GetMapping("/nao-lidas/total")
    public TotalNaoLidasResponse totalNaoLidas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new TotalNaoLidasResponse(notificacaoService.totalNaoLidas(usuario));
    }

    @PatchMapping("/{id}/lida")
    public NotificacaoResponse marcarLida(@PathVariable UUID id,
                                          @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return notificacaoService.marcarLida(id, usuario);
    }

    /** Marca todas como lidas. */
    @PatchMapping("/lidas")
    public ResponseEntity<Void> marcarTodasLidas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        notificacaoService.marcarTodasLidas(usuario);
        return ResponseEntity.noContent().build();
    }
}
