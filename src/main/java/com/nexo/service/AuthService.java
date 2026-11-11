package com.nexo.service;

import com.nexo.dto.LoginRequest;
import com.nexo.dto.LoginResponse;
import com.nexo.dto.MeResponse;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.JwtService;
import com.nexo.security.UsuarioAutenticado;
import java.util.List;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Autentica credenciais resolvendo o usuário dentro do tenant correto. */
@Service
public class AuthService {

    private static final String ERRO_CREDENCIAIS = "E-mail ou senha inválidos";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final PerfilService perfilService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       UsuarioRepository usuarioRepository, TenantRepository tenantRepository,
                       PerfilService perfilService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.perfilService = perfilService;
    }

    /**
     * Se o e-mail existir em mais de um tenant, exige tenantSubdominio para
     * selecionar a identidade correta. A senha continua sendo validada pelo
     * AuthenticationManager/PasswordEncoder do Spring Security.
     */
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim();
        Usuario candidato = resolverUsuario(email, request.tenantSubdominio());

        Authentication autenticacao = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(candidato.getId().toString(), request.senha()));
        UsuarioAutenticado usuario = (UsuarioAutenticado) autenticacao.getPrincipal();
        JwtService.Token token = jwtService.gerar(usuario);
        return LoginResponse.bearer(token.valor(), token.expiraEm(), usuarioAtual(usuario));
    }

    public MeResponse usuarioAtual(UsuarioAutenticado usuario) {
        return perfilService.dadosDe(usuario.getId(), usuario.getTenantId());
    }

    private Usuario resolverUsuario(String email, String subdominioPedido) {
        if (subdominioPedido != null && !subdominioPedido.isBlank()) {
            String subdominio = subdominioPedido.trim().toLowerCase(Locale.ROOT);
            Tenant tenant = tenantRepository.findBySubdominioIgnoreCase(subdominio)
                    .orElseThrow(() -> new BadCredentialsException(ERRO_CREDENCIAIS));
            return usuarioRepository.findByTenantIdAndEmailIgnoreCase(tenant.getId(), email)
                    .orElseThrow(() -> new BadCredentialsException(ERRO_CREDENCIAIS));
        }
        List<Usuario> candidatos = usuarioRepository.findTop2ByEmailIgnoreCaseOrderByIdAsc(email);
        if (candidatos.size() != 1) {
            throw new BadCredentialsException(ERRO_CREDENCIAIS);
        }
        return candidatos.get(0);
    }
}
