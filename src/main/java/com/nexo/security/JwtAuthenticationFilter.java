package com.nexo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a requisição a partir do cabeçalho {@code Authorization: Bearer <token>}.
 *
 * <p>Requisição sem token ou com token inválido segue adiante sem autenticação —
 * quem decide se isso vira 401 é a cadeia de segurança, conforme a rota exigir.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXO_BEARER = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extrairToken(request)
                    .flatMap(jwtService::extrairIdentidade)
                    .ifPresent(identidade -> autenticar(
                            identidade.usuarioId(), identidade.versaoSenha(), request));
        }
        chain.doFilter(request, response);
    }

    private Optional<String> extrairToken(HttpServletRequest request) {
        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(cabecalho) || !cabecalho.startsWith(PREFIXO_BEARER)) {
            return Optional.empty();
        }
        String token = cabecalho.substring(PREFIXO_BEARER.length()).trim();
        return StringUtils.hasText(token) ? Optional.of(token) : Optional.empty();
    }

    private void autenticar(java.util.UUID usuarioId, int versaoSenha, HttpServletRequest request) {
        try {
            UsuarioAutenticado usuario = userDetailsService.loadUserById(usuarioId);
            // O token continua válido, mas o usuário pode ter sido desativado depois de emitido.
            if (!usuario.isEnabled()) {
                log.debug("Token válido de usuário inativo ignorado");
                return;
            }
            if (usuario.getVersaoSenha() != versaoSenha) {
                log.debug("Token JWT de uma versão de senha anterior ignorado");
                return;
            }
            UsernamePasswordAuthenticationToken autenticacao =
                    new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
            autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(autenticacao);
        } catch (UsernameNotFoundException ex) {
            log.debug("Token válido de usuário inexistente ignorado");
        }
    }
}
