package com.nexo.service.recomendacao;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexo.config.IaProperties;
import com.nexo.dto.trilha.SugestaoTrilhaResponse;
import com.nexo.dto.trilha.SugestoesDeTrilhaResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Validade e escopo do cache de sugestões (issue #63). */
class CacheDeSugestoesTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");
    private static final Duration VALIDADE = Duration.ofMinutes(15);

    private RelogioAjustavel relogio;
    private CacheDeSugestoes cache;
    private UUID tenantId;
    private UUID usuarioId;

    @BeforeEach
    void prepara() {
        relogio = new RelogioAjustavel(AGORA);
        cache = new CacheDeSugestoes(relogio,
                new IaProperties("fake", null, null, null, null, null, null, null, null, null, null,
                        new IaProperties.Sugestao(5, 60, VALIDADE)));
        tenantId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
    }

    @Test
    void dentroDaValidadeDevolveOQueFoiGuardado() {
        cache.guardar(tenantId, usuarioId, resposta(UUID.randomUUID()));

        relogio.avancar(VALIDADE.minusSeconds(1));

        assertThat(cache.buscar(tenantId, usuarioId)).isPresent();
    }

    @Test
    void passadaAValidadeEsquece() {
        cache.guardar(tenantId, usuarioId, resposta(UUID.randomUUID()));

        relogio.avancar(VALIDADE.plusSeconds(1));

        assertThat(cache.buscar(tenantId, usuarioId)).isEmpty();
        assertThat(cache.idsGuardados(tenantId, usuarioId)).isEmpty();
    }

    @Test
    void invalidarEsqueceSoOUsuarioPedido() {
        UUID outroUsuario = UUID.randomUUID();
        cache.guardar(tenantId, usuarioId, resposta(UUID.randomUUID()));
        cache.guardar(tenantId, outroUsuario, resposta(UUID.randomUUID()));

        cache.invalidar(tenantId, usuarioId);

        assertThat(cache.buscar(tenantId, usuarioId)).isEmpty();
        assertThat(cache.buscar(tenantId, outroUsuario)).isPresent();
    }

    @Test
    void invalidarTenantEsqueceTodosDoTenantESoDele() {
        UUID outroUsuario = UUID.randomUUID();
        UUID outroTenant = UUID.randomUUID();
        cache.guardar(tenantId, usuarioId, resposta(UUID.randomUUID()));
        cache.guardar(tenantId, outroUsuario, resposta(UUID.randomUUID()));
        cache.guardar(outroTenant, usuarioId, resposta(UUID.randomUUID()));

        cache.invalidarTenant(tenantId);

        assertThat(cache.buscar(tenantId, usuarioId)).isEmpty();
        assertThat(cache.buscar(tenantId, outroUsuario)).isEmpty();
        assertThat(cache.buscar(outroTenant, usuarioId)).isPresent();
    }

    @Test
    void mesmoIdDeUsuarioEmTenantsDiferentesNaoSeMistura() {
        UUID outroTenant = UUID.randomUUID();
        cache.guardar(tenantId, usuarioId, resposta(UUID.randomUUID()));

        assertThat(cache.buscar(outroTenant, usuarioId)).isEmpty();
    }

    @Test
    void idsGuardadosSaemNaOrdemDaSugestao() {
        UUID primeira = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        cache.guardar(tenantId, usuarioId, resposta(primeira, segunda));

        assertThat(cache.idsGuardados(tenantId, usuarioId)).containsExactly(primeira, segunda);
    }

    private static SugestoesDeTrilhaResponse resposta(UUID... trilhas) {
        return SugestoesDeTrilhaResponse.daIa(Stream.of(trilhas)
                .map(id -> new SugestaoTrilhaResponse(id, "Trilha", null, null, "motivo"))
                .toList());
    }

    /** Relógio de teste: o cache só olha as horas, então basta poder avançá-las. */
    private static final class RelogioAjustavel extends Clock {

        private Instant agora;

        private RelogioAjustavel(Instant inicio) {
            this.agora = inicio;
        }

        private void avancar(Duration quanto) {
            agora = agora.plus(quanto);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
