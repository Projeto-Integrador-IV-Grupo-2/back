package com.nexo.security;

import com.nexo.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Emite e valida os tokens JWT de acesso da API.
 *
 * <p>O token é assinado em HMAC-SHA com a chave de {@code app.jwt.secret} e
 * carrega o identificador do usuário no {@code subject}. O filtro usa o subject
 * assinado para recarregar o usuário e suas permissões atuais do banco; os dados
 * redundantes do token nunca definem o tenant nem o papel autorizado.</p>
 */
@Service
public class JwtService {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_PAPEL = "papel";
    static final String CLAIM_TENANT = "tenantId";
    static final String CLAIM_VERSAO_SENHA = "versaoSenha";

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey chave;
    private final String emissor;
    private final Duration validade;

    public JwtService(JwtProperties propriedades) {
        this.chave = Keys.hmacShaKeyFor(propriedades.secret().getBytes(StandardCharsets.UTF_8));
        this.emissor = propriedades.issuer();
        this.validade = propriedades.expiration();
    }

    /** Gera um token de acesso para o usuário recém-autenticado. */
    public Token gerar(UsuarioAutenticado usuario) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(validade);
        String valor = Jwts.builder()
                .subject(usuario.getId().toString())
                .issuer(emissor)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiraEm))
                .claim(CLAIM_EMAIL, usuario.getEmail())
                .claim(CLAIM_PAPEL, usuario.getPapel().name())
                .claim(CLAIM_TENANT, usuario.getTenantId().toString())
                .claim(CLAIM_VERSAO_SENHA, usuario.getVersaoSenha())
                .signWith(chave)
                .compact();
        return new Token(valor, expiraEm);
    }

    /**
     * Valida assinatura, emissor e expiração.
     *
     * @return as claims do token, ou vazio quando ele é inválido por qualquer motivo
     */
    public Optional<Claims> validar(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(chave)
                    .requireIssuer(emissor)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token JWT rejeitado: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** UUID do usuário extraído do subject assinado, quando o token é válido. */
    public Optional<UUID> extrairUsuarioId(String token) {
        return extrairIdentidade(token).map(IdentidadeToken::usuarioId);
    }

    /** Identidade assinada e versão de senha do token; tokens legados usam a versão inicial. */
    public Optional<IdentidadeToken> extrairIdentidade(String token) {
        return validar(token).flatMap(claims -> {
            try {
                UUID usuarioId = UUID.fromString(claims.getSubject());
                Integer versaoSenha = claims.get(CLAIM_VERSAO_SENHA, Integer.class);
                return Optional.of(new IdentidadeToken(usuarioId, versaoSenha == null ? 0 : versaoSenha));
            } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
                log.debug("Subject JWT rejeitado: {}", ex.getMessage());
                return Optional.empty();
            }
        });
    }

    /** E-mail do dono do token, útil para diagnóstico, nunca para autorizar acesso. */
    public Optional<String> extrairEmail(String token) {
        return validar(token).map(claims -> claims.get(CLAIM_EMAIL, String.class));
    }

    /** Token assinado e o instante em que ele deixa de valer. */
    public record Token(String valor, Instant expiraEm) {
    }

    public record IdentidadeToken(UUID usuarioId, int versaoSenha) {
    }
}
