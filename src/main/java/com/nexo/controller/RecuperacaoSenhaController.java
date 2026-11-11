package com.nexo.controller;

import com.nexo.dto.senha.EsqueciSenhaRequest;
import com.nexo.dto.senha.RedefinirSenhaRequest;
import com.nexo.service.senha.RecuperacaoSenhaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recuperação de senha (issue #76): rotas públicas, liberadas no
 * {@code SecurityConfig}. Nenhuma regra vive aqui — tudo é do
 * {@link RecuperacaoSenhaService}.
 */
@RestController
@RequestMapping("/api/auth")
public class RecuperacaoSenhaController {

    private final RecuperacaoSenhaService recuperacaoSenhaService;

    public RecuperacaoSenhaController(RecuperacaoSenhaService recuperacaoSenhaService) {
        this.recuperacaoSenhaService = recuperacaoSenhaService;
    }

    /**
     * Sempre 204, exista ou não a conta: a resposta não revela quem tem
     * cadastro. O IP respeita os cabeçalhos do proxy configurados pelo
     * {@code server.forward-headers-strategy}; só proxies confiáveis devem
     * encaminhar esses cabeçalhos.
     */
    @PostMapping("/esqueci-senha")
    public ResponseEntity<Void> esqueciSenha(@Valid @RequestBody EsqueciSenhaRequest request,
                                             HttpServletRequest http) {
        recuperacaoSenhaService.solicitar(request.email(), request.tenantSubdominio(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
        recuperacaoSenhaService.redefinir(request.token(), request.novaSenha());
        return ResponseEntity.noContent().build();
    }
}
