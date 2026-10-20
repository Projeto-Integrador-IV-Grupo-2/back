package com.nexo.security;

import com.nexo.entity.Perfil;
import com.nexo.entity.Usuario;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuário autenticado exposto ao Spring Security.
 *
 * <p>Cópia imutável dos dados do {@link Usuario} necessários para autenticar e
 * autorizar: nada aqui depende da sessão do JPA, então pode circular livremente
 * pelo filtro, pelo contexto de segurança e pelos controllers.</p>
 */
public class UsuarioAutenticado implements UserDetails {

    private static final String PREFIXO_ROLE = "ROLE_";

    private final UUID id;
    private final UUID tenantId;
    private final String nomeCompleto;
    private final String email;
    private final String senhaHash;
    private final Perfil papel;
    private final boolean ativo;

    public UsuarioAutenticado(UUID id, UUID tenantId, String nomeCompleto, String email,
                              String senhaHash, Perfil papel, boolean ativo) {
        this.id = id;
        this.tenantId = tenantId;
        this.nomeCompleto = nomeCompleto;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papel = papel;
        this.ativo = ativo;
    }

    public static UsuarioAutenticado de(Usuario usuario) {
        return new UsuarioAutenticado(
                usuario.getId(),
                usuario.getTenant().getId(),
                usuario.getNomeCompleto(),
                usuario.getEmail(),
                usuario.getSenhaHash(),
                usuario.getPapel(),
                usuario.isAtivo() && usuario.getTenant().isAtivo());
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public String getEmail() {
        return email;
    }

    public Perfil getPapel() {
        return papel;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(PREFIXO_ROLE + papel.name()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }
}
