package com.nexo.controller;

import com.nexo.dto.trilha.TrilhaDetalheResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.TrilhaService;
import java.util.UUID;
import com.nexo.dto.trilha.TrilhaResumoResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.TrilhaService;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
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

    /**
     * Lista as trilhas ativas do tenant do usuário autenticado (Card 05).
     *
     * <p>Sem restrição de perfil, pelo mesmo motivo do detalhe abaixo: o card
     * de segurança exige que a listagem fique acessível a <em>qualquer</em>
     * autenticado do próprio tenant. Quem impede ver trilha de outra empresa
     * não é anotação nenhuma — é o filtro por tenant no
     * {@link TrilhaService#listar}.</p>
     */
    @GetMapping
    public PagedModel<TrilhaResumoResponse> listar(
            @PageableDefault(size = 20, sort = "nome") Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new PagedModel<>(trilhaService.listar(pageable, usuario));
    }

    @GetMapping("/{id}")
    public TrilhaDetalheResponse detalhe(@PathVariable UUID id,
                                          @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return trilhaService.buscarDetalhe(id, usuario);
    }
}
