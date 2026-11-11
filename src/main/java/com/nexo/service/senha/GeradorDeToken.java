package com.nexo.service.senha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Gera os tokens dos links de redefinição e o hash que vai para o banco
 * (issue #76). Singleton do Spring e sem estado — o {@link SecureRandom} é
 * seguro para uso entre threads.
 *
 * <p>32 bytes aleatórios em Base64 URL (cabem na URL sem escapar). O banco
 * guarda só o SHA-256 em hex: quem lê a tabela não consegue montar o link.
 * Um hash simples basta aqui — ao contrário de uma senha, o token tem 256 bits
 * de entropia, então não dá para adivinhá-lo pelo hash.</p>
 */
@Component
public class GeradorDeToken {

    private static final int BYTES = 32;

    private final SecureRandom aleatorio = new SecureRandom();

    /** Token novo: o valor (só para o e-mail) e o hash (só para o banco). */
    public TokenGerado gerar() {
        byte[] bytes = new byte[BYTES];
        aleatorio.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new TokenGerado(valor, hash(valor));
    }

    /** SHA-256 em hex minúsculo; também usado nas chaves do limite (e-mail e IP). */
    public String hash(String valor) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", ex);
        }
    }

    /**
     * @param valor o token em claro — vai só no link do e-mail
     * @param hash  o SHA-256 dele — vai só para o banco
     */
    public record TokenGerado(String valor, String hash) {

        @Override
        public String toString() {
            return "TokenGerado[***]";
        }
    }
}
