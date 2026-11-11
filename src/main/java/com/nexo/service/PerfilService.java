package com.nexo.service;

import com.nexo.dto.MeResponse;
import com.nexo.dto.perfil.AtualizarPerfilRequest;
import com.nexo.dto.perfil.TrocarSenhaRequest;
import com.nexo.entity.Usuario;
import com.nexo.exception.CampoInvalidoException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil do próprio usuário: ler os dados, mudar o nome e trocar a senha.
 *
 * <p>Sempre sobre o dono do token — não há id na rota, então ninguém altera o
 * perfil de outra pessoa. Como o filtro do JWT recarrega o usuário do banco a
 * cada requisição, o nome novo já vale no próximo {@code GET /me}, sem token
 * novo.</p>
 *
 * <p>Senha atual errada é <strong>400 com {@code fieldErrors}</strong>, nunca
 * 401: o front trata 401 como sessão vencida e desloga.</p>
 */
@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public PerfilService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Dados do usuário com área e empresa, como o login e o {@code GET /me} devolvem. */
    @Transactional(readOnly = true)
    public MeResponse dadosDe(UUID usuarioId, UUID tenantId) {
        return MeResponse.de(buscar(usuarioId, tenantId));
    }

    @Transactional
    public MeResponse atualizar(UsuarioAutenticado usuario, AtualizarPerfilRequest request) {
        Usuario atual = buscar(usuario.getId(), usuario.getTenantId());
        atual.setNomeCompleto(request.nomeCompleto().strip());
        return MeResponse.de(usuarioRepository.saveAndFlush(atual));
    }

    @Transactional
    public void trocarSenha(UsuarioAutenticado usuario, TrocarSenhaRequest request) {
        Usuario atual = buscar(usuario.getId(), usuario.getTenantId());
        if (!passwordEncoder.matches(request.senhaAtual(), atual.getSenhaHash())) {
            throw new CampoInvalidoException("senhaAtual", "Senha atual incorreta.");
        }
        if (passwordEncoder.matches(request.novaSenha(), atual.getSenhaHash())) {
            throw new CampoInvalidoException("novaSenha", "A nova senha precisa ser diferente da atual.");
        }
        atual.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        atual.incrementarVersaoSenha();
        usuarioRepository.save(atual);
    }

    private Usuario buscar(UUID usuarioId, UUID tenantId) {
        return usuarioRepository.findById(usuarioId)
                .filter(usuario -> usuario.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + usuarioId));
    }
}
