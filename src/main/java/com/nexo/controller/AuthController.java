package com.nexo.controller;

import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.dto.MeResponse;
import com.nexo.dto.perfil.AtualizarPerfilRequest;
import com.nexo.dto.perfil.TrocarSenhaRequest;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AuthService;
import com.nexo.service.PerfilService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticação: login (público), dados do usuário logado e o
 * perfil dele (nome e senha), sempre sobre o dono do token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PerfilService perfilService;

    public AuthController(AuthService authService, PerfilService perfilService) {
        this.authService = authService;
        this.perfilService = perfilService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(authService.usuarioAtual(usuario));
    }

    /** O próprio usuário muda o nome; devolve o perfil como ficou gravado. */
    @PutMapping("/me")
    public MeResponse atualizarPerfil(@Valid @RequestBody AtualizarPerfilRequest request,
                                      @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return perfilService.atualizar(usuario, request);
    }

    /** Troca a própria senha, conferindo a atual. Senha atual errada é 400, nunca 401. */
    @PatchMapping("/me/senha")
    public ResponseEntity<Void> trocarSenha(@Valid @RequestBody TrocarSenhaRequest request,
                                            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        perfilService.trocarSenha(usuario, request);
        return ResponseEntity.noContent().build();
    }
}
