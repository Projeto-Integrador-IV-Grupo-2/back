package com.nexo.service;

import com.nexo.dto.empresa.CriarEmpresaRequest;
import com.nexo.dto.empresa.EmpresaResponse;
import com.nexo.entity.Perfil;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.exception.CampoInvalidoException;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de empresas (tenants) pela equipe da plataforma.
 *
 * <p>Só o ADMIN da empresa dona da plataforma ({@link Tenant#SUBDOMINIO_PLATAFORMA})
 * cadastra e lista empresas; o ADMIN de qualquer outra empresa recebe 403. A
 * empresa nasce com um administrador, que monta o resto (áreas, usuários,
 * conteúdos) já dentro dela — cada empresa só enxerga os próprios dados.</p>
 */
@Service
public class EmpresaService {

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public EmpresaService(TenantRepository tenantRepository, UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<EmpresaResponse> listar(UsuarioAutenticado requisitante) {
        exigirPlataforma(requisitante);
        return tenantRepository.findAllByOrderByCriadoEmAsc().stream()
                .map(tenant -> EmpresaResponse.de(tenant, usuarioRepository.countByTenantId(tenant.getId())))
                .toList();
    }

    @Transactional
    public EmpresaResponse criar(CriarEmpresaRequest request, UsuarioAutenticado requisitante) {
        exigirPlataforma(requisitante);
        String subdominio = request.subdominio().trim().toLowerCase(Locale.ROOT);
        if (tenantRepository.existsBySubdominioIgnoreCase(subdominio)) {
            throw new CampoInvalidoException("subdominio", "Já existe uma empresa com o subdomínio: " + subdominio);
        }

        Tenant tenant = new Tenant();
        tenant.setNome(request.nome().trim());
        tenant.setSubdominio(subdominio);
        tenant.setCnpj(request.cnpj() == null || request.cnpj().isBlank() ? null : request.cnpj().trim());

        Usuario administrador = new Usuario();
        administrador.setNomeCompleto(request.nomeAdministrador().trim());
        administrador.setEmail(request.emailAdministrador().trim().toLowerCase(Locale.ROOT));
        administrador.setSenhaHash(passwordEncoder.encode(request.senhaAdministrador()));
        administrador.setPapel(Perfil.ADMIN);
        administrador.setDataEntrada(LocalDate.now());

        try {
            Tenant salvo = tenantRepository.saveAndFlush(tenant);
            administrador.setTenant(salvo);
            usuarioRepository.saveAndFlush(administrador);
            return EmpresaResponse.de(salvo, 1);
        } catch (DataIntegrityViolationException ex) {
            // Corrida entre a verificação acima e a gravação: a UNIQUE do banco decide.
            throw new CampoInvalidoException("subdominio", "Já existe uma empresa com o subdomínio: " + subdominio);
        }
    }

    private void exigirPlataforma(UsuarioAutenticado requisitante) {
        boolean daPlataforma = requisitante.getPapel() == Perfil.ADMIN && tenantRepository
                .findById(requisitante.getTenantId())
                .map(Tenant::isPlataforma)
                .orElse(false);
        if (!daPlataforma) {
            throw new AccessDeniedException("Só a equipe da plataforma cadastra empresas");
        }
    }
}
