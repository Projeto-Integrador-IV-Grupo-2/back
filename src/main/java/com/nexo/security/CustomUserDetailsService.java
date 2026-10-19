package com.nexo.security;

import com.nexo.entity.Usuario;
import com.nexo.repository.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega usuários para o login e para a validação de tokens.
 *
 * <p>O login inicial resolve o e-mail no contexto do tenant (ou exige unicidade
 * global da ocorrência); depois o AuthenticationManager autentica pelo UUID.
 * Tokens também carregam o UUID como subject. E-mail nunca é usado para
 * reconstruir a identidade de uma requisição autenticada.</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        // O AuthService passa o UUID já resolvido após escolher o tenant no login.
        try {
            return loadUserById(UUID.fromString(username));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Compatibilidade com autenticação por e-mail em fluxos internos; ambiguidade é negada.
        }
        List<Usuario> candidatos = usuarioRepository.findTop2ByEmailIgnoreCaseOrderByIdAsc(username);
        if (candidatos.size() != 1) {
            throw new UsernameNotFoundException("Usuário não encontrado");
        }
        return UsuarioAutenticado.de(candidatos.get(0));
    }

    @Transactional(readOnly = true)
    public UsuarioAutenticado loadUserById(UUID id) {
        return usuarioRepository.findById(id)
                .map(UsuarioAutenticado::de)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
