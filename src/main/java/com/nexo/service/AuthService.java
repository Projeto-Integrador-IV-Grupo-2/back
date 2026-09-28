package com.nexo.service;

import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.dto.MeResponse;
import com.nexo.security.JwtService;
import com.nexo.security.UsuarioAutenticado;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Regra de autenticação: confere as credenciais e emite o token de acesso.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Autentica e-mail e senha e devolve o token de acesso.
     *
     * @throws org.springframework.security.core.AuthenticationException quando as
     *         credenciais não conferem ou o usuário está inativo
     */
    public LoginResponse login(LoginRequest request) {
        Authentication autenticacao = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.senha()));

        UsuarioAutenticado usuario = (UsuarioAutenticado) autenticacao.getPrincipal();
        JwtService.Token token = jwtService.gerar(usuario);

        return LoginResponse.bearer(
                token.valor(), token.expiraEm(), MeResponse.de(usuario));
    }

    public MeResponse usuarioAtual(UsuarioAutenticado usuario) {
        return MeResponse.de(usuario);
    }
}
