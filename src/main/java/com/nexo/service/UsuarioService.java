package com.nexo.service;

import com.nexo.dto.usuario.AtualizarUsuarioRequest;
import com.nexo.dto.usuario.CriarUsuarioRequest;
import com.nexo.dto.usuario.UsuarioResponse;
import com.nexo.entity.Area;
import com.nexo.entity.Cargo;
import com.nexo.entity.SituacaoUsuario;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import com.nexo.exception.EmailDuplicadoException;
import com.nexo.exception.OperacaoNaoPermitidaException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.AreaRepository;
import com.nexo.repository.CargoRepository;
import com.nexo.repository.UsuarioFiltro;
import com.nexo.repository.UsuarioRepository;
import com.nexo.repository.UsuarioSpecifications;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio de cadastro e gerenciamento de usuários administrativos.
 *
 * <p><strong>Isolamento por tenant:</strong> este é um sistema multi-tenant
 * (ver {@code Tenant}/{@code Usuario.tenant}). Todo método aqui recebe o
 * e-mail do administrador autenticado ({@code requisitanteEmail}) e resolve
 * o tenant a partir dele — nunca de um parâmetro vindo do cliente. Toda
 * consulta, criação e edição é restrita a esse tenant; buscar/editar um
 * usuário de outro tenant resulta em {@link ResourceNotFoundException}
 * (nunca 403), para não revelar a um administrador que aquele registro
 * existe em outra empresa.
 *
 * <p>Hoje isso exige uma consulta extra (buscar o Usuario do próprio
 * requisitante para ler {@code getTenant()}), porque a autenticação
 * JWT/tenant-claim ainda não está integrada nesta branch. Quando o módulo de
 * auth (branch {@code feat/autenticacao-jwt}) for mesclado e o tenant passar
 * a vir como claim do token, {@link #tenantDoRequisitante} pode ser
 * simplificado para ler direto da autenticação, sem ida ao banco.</p>
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final CargoRepository cargoRepository;
    private final AreaRepository areaRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository,
                           CargoRepository cargoRepository,
                           AreaRepository areaRepository,
                           PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.cargoRepository = cargoRepository;
        this.areaRepository = areaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UsuarioResponse criar(CriarUsuarioRequest request, String requisitanteEmail) {
        Tenant tenant = tenantDoRequisitante(requisitanteEmail);
        String email = request.email().trim().toLowerCase();

        if (usuarioRepository.existsByTenantIdAndEmailIgnoreCase(tenant.getId(), email)) {
            throw new EmailDuplicadoException(email);
        }

        Usuario usuario = new Usuario();
        usuario.setTenant(tenant);
        usuario.setNomeCompleto(request.nomeCompleto().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        usuario.setPapel(request.papel());
        usuario.setCargo(resolverCargo(request.cargoId(), tenant));
        usuario.setArea(resolverArea(request.areaId(), tenant));
        usuario.setDataEntrada(request.dataEntrada());

        try {
            return UsuarioResponse.from(usuarioRepository.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException ex) {
            // Segunda camada de proteção: cobre a corrida entre a verificação
            // acima e a gravação. saveAndFlush (em vez de save) é essencial
            // aqui — como o id é gerado em memória (GenerationType.UUID), um
            // save() comum só dispararia o INSERT no commit da transação,
            // depois deste método já ter retornado, e a violação da UNIQUE
            // (tenant_id, email) nunca cairia neste catch. A UNIQUE do banco
            // é quem garante a consistência final; aqui só traduzimos para o
            // erro de negócio.
            throw new EmailDuplicadoException(email);
        }
    }

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(UsuarioFiltro filtro, Pageable pageable, String requisitanteEmail) {
        Tenant tenant = tenantDoRequisitante(requisitanteEmail);
        Specification<Usuario> especificacao = UsuarioSpecifications.comFiltro(filtro)
                .and((root, query, cb) -> cb.equal(root.get("tenant").get("id"), tenant.getId()));
        return usuarioRepository.findAll(especificacao, pageable).map(UsuarioResponse::from);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(UUID id, String requisitanteEmail) {
        return UsuarioResponse.from(buscarEntidadeNoTenant(id, requisitanteEmail));
    }

    @Transactional
    public UsuarioResponse atualizar(UUID id, AtualizarUsuarioRequest request, String requisitanteEmail) {
        Usuario usuario = buscarEntidadeNoTenant(id, requisitanteEmail);
        usuario.setNomeCompleto(request.nomeCompleto().trim());
        usuario.setCargo(resolverCargo(request.cargoId(), usuario.getTenant()));
        usuario.setArea(resolverArea(request.areaId(), usuario.getTenant()));
        usuario.setPapel(request.papel());
        // saveAndFlush (não save) para que atualizadoEm (@UpdateTimestamp,
        // só é preenchido no flush) já venha atualizado na resposta — sem
        // isso, o valor lido seria o antigo, de antes desta mudança.
        return UsuarioResponse.from(usuarioRepository.saveAndFlush(usuario));
    }

    /**
     * Ativa ou desativa um usuário (soft delete via {@code Usuario.ativo}).
     * Um administrador nunca pode desativar a própria conta — a comparação é
     * feita pelo e-mail do solicitante (identidade extraída da autenticação
     * atual pelo controller), nunca por um campo vindo do corpo da requisição.
     */
    @Transactional
    public UsuarioResponse atualizarSituacao(UUID id, SituacaoUsuario novaSituacao, String requisitanteEmail) {
        Usuario usuario = buscarEntidadeNoTenant(id, requisitanteEmail);

        if (novaSituacao == SituacaoUsuario.INATIVO && usuario.getEmail().equalsIgnoreCase(requisitanteEmail)) {
            throw new OperacaoNaoPermitidaException("Um administrador não pode desativar a própria conta");
        }

        usuario.setSituacao(novaSituacao);
        return UsuarioResponse.from(usuarioRepository.saveAndFlush(usuario));
    }

    private Usuario buscarEntidadeNoTenant(UUID id, String requisitanteEmail) {
        Tenant tenant = tenantDoRequisitante(requisitanteEmail);
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + id));
        if (!usuario.getTenant().getId().equals(tenant.getId())) {
            throw new ResourceNotFoundException("Usuário não encontrado: " + id);
        }
        return usuario;
    }

    private Tenant tenantDoRequisitante(String requisitanteEmail) {
        return usuarioRepository.findByEmailIgnoreCase(requisitanteEmail)
                .map(Usuario::getTenant)
                .orElseThrow(() -> new IllegalStateException(
                        "Usuário autenticado não encontrado na base: " + requisitanteEmail));
    }

    private Cargo resolverCargo(UUID cargoId, Tenant tenant) {
        if (cargoId == null) {
            return null;
        }
        Cargo cargo = cargoRepository.findById(cargoId)
                .orElseThrow(() -> new ResourceNotFoundException("Cargo não encontrado: " + cargoId));
        exigirMesmoTenant(cargo.getTenant(), tenant, "Cargo");
        return cargo;
    }

    private Area resolverArea(UUID areaId, Tenant tenant) {
        if (areaId == null) {
            return null;
        }
        Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new ResourceNotFoundException("Área não encontrada: " + areaId));
        exigirMesmoTenant(area.getTenant(), tenant, "Área");
        return area;
    }

    /** Recusa referenciar um cargo/área de outro tenant — sem essa checagem, um admin poderia "colar" catálogos entre empresas. */
    private void exigirMesmoTenant(Tenant recursoTenant, Tenant tenantAtual, String nomeRecurso) {
        if (!recursoTenant.getId().equals(tenantAtual.getId())) {
            throw new ResourceNotFoundException(nomeRecurso + " não encontrado(a) para este tenant");
        }
    }
}
