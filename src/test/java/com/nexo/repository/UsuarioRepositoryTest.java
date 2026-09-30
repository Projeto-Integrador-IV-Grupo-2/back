package com.nexo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexo.entity.Area;
import com.nexo.entity.Perfil;
import com.nexo.entity.SituacaoUsuario;
import com.nexo.entity.Tenant;
import com.nexo.entity.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class UsuarioRepositoryTest {

    private static final String BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private TestEntityManager em;

    private Tenant tenant;

    @BeforeEach
    void carregaTenantDoSeed() {
        tenant = tenantRepository.findBySubdominio("nexo").orElseThrow();
    }

    @Test
    void mapeamentoRefleteOSchema() {
        assertThat(usuarioRepository.count()).isPositive();
    }

    @Test
    void buscaUsuarioPorEmailIgnorandoCaixa() {
        Optional<Usuario> encontrado = usuarioRepository.findByEmailIgnoreCase("ADMIN@NEXO.COM");

        assertThat(encontrado).isPresent();
        Usuario admin = encontrado.get();
        assertThat(admin.getPapel()).isEqualTo(Perfil.ADMIN);
        assertThat(admin.getSituacao()).isEqualTo(SituacaoUsuario.ATIVO);
        assertThat(admin.getTenant().getSubdominio()).isEqualTo("nexo");
        assertThat(admin.getCriadoEm()).isNotNull();
        assertThat(admin.getAtualizadoEm()).isNotNull();
    }

    @Test
    void buscaPorEmailDentroDoTenant() {
        assertThat(usuarioRepository.findByTenantIdAndEmailIgnoreCase(tenant.getId(), "admin@nexo.com"))
                .isPresent();
        assertThat(usuarioRepository.findByTenantIdAndEmailIgnoreCase(UUID.randomUUID(), "admin@nexo.com"))
                .isEmpty();
    }

    @Test
    void verificaExistenciaDeEmail() {
        assertThat(usuarioRepository.existsByEmailIgnoreCase("admin@nexo.com")).isTrue();
        assertThat(usuarioRepository.existsByEmailIgnoreCase("naoexiste@nexo.com")).isFalse();
    }

    @Test
    void salvaUsuarioEPreencheTimestamps() {
        Usuario salvo = usuarioRepository.saveAndFlush(
                novoUsuario("maria@nexo.com", Perfil.COLABORADOR, SituacaoUsuario.ATIVO));
        em.clear();

        Usuario recarregado = usuarioRepository.findById(salvo.getId()).orElseThrow();
        assertThat(recarregado.getCriadoEm()).isNotNull();
        assertThat(recarregado.getAtualizadoEm()).isNotNull();
        assertThat(recarregado.getPapel()).isEqualTo(Perfil.COLABORADOR);
        assertThat(recarregado.getSituacao()).isEqualTo(SituacaoUsuario.ATIVO);
    }

    @Test
    void situacaoInativaEhGravadaComoBooleanFalse() {
        Usuario salvo = usuarioRepository.saveAndFlush(
                novoUsuario("joao.inativo@nexo.com", Perfil.COLABORADOR, SituacaoUsuario.INATIVO));
        em.clear();

        Object ativoNaColuna = em.getEntityManager()
                .createNativeQuery("SELECT ativo FROM usuario WHERE id = :id")
                .setParameter("id", salvo.getId())
                .getSingleResult();

        assertThat(ativoNaColuna).isEqualTo(Boolean.FALSE);
        assertThat(usuarioRepository.findById(salvo.getId()).orElseThrow().getSituacao())
                .isEqualTo(SituacaoUsuario.INATIVO);
    }

    @Test
    void consultaPaginadaComFiltroDePerfil() {
        Page<Usuario> pagina = usuarioRepository.findAll(
                UsuarioSpecifications.comFiltro(new UsuarioFiltro(null, null, null, Perfil.ADMIN, null)),
                PageRequest.of(0, 20, Sort.by("email")));

        assertThat(pagina.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(pagina.getContent()).isNotEmpty();
        assertThat(pagina.getContent())
                .allSatisfy(u -> assertThat(u.getPapel()).isEqualTo(Perfil.ADMIN));
    }

    @Test
    void consultaPaginadaComFiltroDeNomeEEmail() {
        usuarioRepository.saveAndFlush(
                novoUsuario("ana.silva@nexo.com", Perfil.COLABORADOR, SituacaoUsuario.ATIVO));
        em.clear();

        Page<Usuario> porNome = usuarioRepository.findAll(
                UsuarioSpecifications.comFiltro(new UsuarioFiltro("ana", null, null, null, null)),
                PageRequest.of(0, 10));
        assertThat(porNome.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(porNome.getContent())
                .allSatisfy(u -> assertThat(u.getNomeCompleto().toLowerCase()).contains("ana"));

        Page<Usuario> semResultado = usuarioRepository.findAll(
                UsuarioSpecifications.comFiltro(
                        new UsuarioFiltro(null, "ninguem@lugar.nenhum", null, null, null)),
                PageRequest.of(0, 10));
        assertThat(semResultado.getTotalElements()).isZero();
    }

    @Test
    void consultaPaginadaComFiltroDeDepartamento() {
        Area departamento = new Area();
        departamento.setTenant(tenant);
        departamento.setNome("TI-" + UUID.randomUUID());
        Area salvo = areaRepository.saveAndFlush(departamento);

        Usuario dev = novoUsuario("dev@nexo.com", Perfil.COLABORADOR, SituacaoUsuario.ATIVO);
        dev.setArea(salvo);
        usuarioRepository.saveAndFlush(dev);
        em.clear();

        Page<Usuario> pagina = usuarioRepository.findAll(
                UsuarioSpecifications.comFiltro(new UsuarioFiltro(null, null, salvo.getId(), null, null)),
                PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).getEmail()).isEqualTo("dev@nexo.com");
    }

    @Test
    void consultaSemFiltroRetornaTudoPaginado() {
        Page<Usuario> pagina = usuarioRepository.findAll(
                UsuarioSpecifications.comFiltro(UsuarioFiltro.vazio()),
                PageRequest.of(0, 1));

        assertThat(pagina.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(pagina.getSize()).isEqualTo(1);
        assertThat(pagina.getContent()).hasSize(1);
    }

    @Test
    void existsByAreaIdRetornaFalsoQuandoNenhumUsuarioEstaVinculado() {
        Area area = new Area();
        area.setTenant(tenant);
        area.setNome("Área sem vínculo-" + UUID.randomUUID());
        Area salva = areaRepository.saveAndFlush(area);

        assertThat(usuarioRepository.existsByAreaId(salva.getId())).isFalse();
    }

    @Test
    void existsByAreaIdRetornaVerdadeiroQuandoHaUsuarioVinculado() {
        Area area = new Area();
        area.setTenant(tenant);
        area.setNome("Área com vínculo-" + UUID.randomUUID());
        Area salva = areaRepository.saveAndFlush(area);

        Usuario vinculado = novoUsuario(
                "vinculado.area." + UUID.randomUUID() + "@nexo.com", Perfil.COLABORADOR, SituacaoUsuario.ATIVO);
        vinculado.setArea(salva);
        usuarioRepository.saveAndFlush(vinculado);
        em.clear();

        assertThat(usuarioRepository.existsByAreaId(salva.getId())).isTrue();
    }

    private Usuario novoUsuario(String email, Perfil papel, SituacaoUsuario situacao) {
        Usuario u = new Usuario();
        u.setTenant(tenant);
        u.setNomeCompleto(nomeApartirDoEmail(email));
        u.setEmail(email);
        u.setSenhaHash(BCRYPT_HASH);
        u.setPapel(papel);
        u.setSituacao(situacao);
        return u;
    }

    private static String nomeApartirDoEmail(String email) {
        String local = email.substring(0, email.indexOf('@')).replace('.', ' ');
        return Character.toUpperCase(local.charAt(0)) + local.substring(1);
    }
}
