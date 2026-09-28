package com.nexo.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexo.config.JwtProperties;
import com.nexo.entity.Perfil;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Testa a emissão e a validação do token sem subir o contexto do Spring.
 */
class JwtServiceTest {

    private static final String SECRET = "nexo-chave-de-teste-automatizado-nao-use-em-producao";
    private static final String EMISSOR = "nexo-api";

    private final JwtService jwtService = new JwtService(
            new JwtProperties(SECRET, EMISSOR, Duration.ofHours(2)));

    @Test
    void tokenGeradoCarregaDadosDoUsuarioEValida() {
        UsuarioAutenticado usuario = usuario(Perfil.ADMIN);

        JwtService.Token token = jwtService.gerar(usuario);

        assertThat(token.valor()).isNotBlank();
        assertThat(token.expiraEm()).isAfter(Instant.now());

        Claims claims = jwtService.validar(token.valor()).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(claims.getIssuer()).isEqualTo(EMISSOR);
        assertThat(claims.get(JwtService.CLAIM_EMAIL, String.class)).isEqualTo(usuario.getEmail());
        assertThat(claims.get(JwtService.CLAIM_PAPEL, String.class)).isEqualTo("ADMIN");
        assertThat(claims.get(JwtService.CLAIM_TENANT, String.class))
                .isEqualTo(usuario.getTenantId().toString());
    }

    @Test
    void extraiEmailDoTokenValido() {
        UsuarioAutenticado usuario = usuario(Perfil.COLABORADOR);

        String token = jwtService.gerar(usuario).valor();

        assertThat(jwtService.extrairEmail(token)).contains(usuario.getEmail());
    }

    @Test
    void tokenAssinadoComOutraChaveEhRejeitado() {
        String tokenDeOutraApi = new JwtService(
                new JwtProperties("outra-chave-de-teste-com-tamanho-suficiente", EMISSOR, Duration.ofHours(2)))
                .gerar(usuario(Perfil.ADMIN))
                .valor();

        assertThat(jwtService.validar(tokenDeOutraApi)).isEmpty();
    }

    @Test
    void tokenDeOutroEmissorEhRejeitado() {
        String tokenDeOutroEmissor = new JwtService(
                new JwtProperties(SECRET, "outra-api", Duration.ofHours(2)))
                .gerar(usuario(Perfil.ADMIN))
                .valor();

        assertThat(jwtService.validar(tokenDeOutroEmissor)).isEmpty();
    }

    @Test
    void tokenExpiradoEhRejeitado() throws InterruptedException {
        // A claim "exp" tem precisão de segundos: a espera precisa ultrapassá-la.
        JwtService comValidadeDeUmSegundo = new JwtService(
                new JwtProperties(SECRET, EMISSOR, Duration.ofSeconds(1)));
        String token = comValidadeDeUmSegundo.gerar(usuario(Perfil.ADMIN)).valor();

        Thread.sleep(1200);

        assertThat(comValidadeDeUmSegundo.validar(token)).isEmpty();
    }

    @Test
    void textoQueNaoEhTokenEhRejeitado() {
        assertThat(jwtService.validar("isto-nao-e-um-token")).isEmpty();
    }

    private static UsuarioAutenticado usuario(Perfil papel) {
        return new UsuarioAutenticado(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Fulano de Teste",
                "fulano@nexo.com",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                papel,
                true);
    }
}
