package com.nexo.controller;

import com.nexo.dto.trilha.TrilhaDetalheResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.TrilhaService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de consulta de trilhas (Card 08).
 *
 * <p>Sem restrição de perfil: qualquer usuário autenticado do tenant pode ver
 * o detalhe de uma trilha e o próprio progresso — a exigência de autenticação
 * (padrão da cadeia de segurança para qualquer rota não listada como pública)
 * já é suficiente aqui.</p>
 *
 * <p>Nenhuma regra de negócio vive aqui — tudo é delegado a
 * {@link TrilhaService}; este controller só traduz HTTP &lt;-&gt; DTO.</p>
 */
@RestController
@RequestMapping("/api/trilhas")
public class TrilhaController {

    private final TrilhaService trilhaService;

    public TrilhaController(TrilhaService trilhaService) {
        this.trilhaService = trilhaService;
    }

    @GetMapping("/{id}")
    public TrilhaDetalheResponse detalhe(@PathVariable UUID id,
                                          @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaService.buscarDetalhe(id, usuario);
    }
}
