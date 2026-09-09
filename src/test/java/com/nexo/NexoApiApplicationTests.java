package com.nexo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sobe o contexto completo da aplicação no perfil {@code test}.
 *
 * <p>O perfil {@code test} usa Testcontainers (PostgreSQL efêmero), portanto
 * exige o Docker em execução na máquina que roda os testes (localmente e no CI).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class NexoApiApplicationTests {

    @Test
    void contextLoads() {
        // Falha caso o contexto Spring não inicialize
        // (configuração, beans, Flyway, JPA, segurança...).
    }
}
