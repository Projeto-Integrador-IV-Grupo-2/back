package com.nexo.service.recomendacao;

import com.nexo.config.IaProperties;
import com.nexo.dto.trilha.SugestaoTrilhaResponse;
import com.nexo.dto.trilha.SugestoesDeTrilhaResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Guarda a última sugestão de cada usuário por um tempo configurável
 * ({@code IA_SUGESTAO_VALIDADE}), para dois acessos seguidos não chamarem o
 * modelo duas vezes.
 *
 * <p>Singleton do Spring, em memória e por instância da API: o pior caso de
 * várias instâncias é uma chamada a mais ao modelo, nunca uma sugestão
 * errada. Iniciar ou concluir uma trilha invalida a entrada do usuário, e
 * inativar ou reativar uma trilha invalida o tenant inteiro. Quem faz isso é
 * o {@code SugestoesDeTrilhaListener}.</p>
 *
 * <p>A chave inclui o tenant: dois usuários de empresas diferentes nunca se
 * misturam, mesmo com ids repetidos.</p>
 */
@Component
public class CacheDeSugestoes {

    /** Teto de entradas vivas; acima disso o cache é esvaziado em vez de crescer sem limite. */
    private static final int MAXIMO_DE_ENTRADAS = 10_000;

    private final Map<Chave, Entrada> entradas = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration validade;

    public CacheDeSugestoes(Clock clock, IaProperties properties) {
        this.clock = clock;
        this.validade = properties.sugestao().validade();
    }

    /** A sugestão guardada do usuário, se ainda estiver dentro da validade. */
    public Optional<SugestoesDeTrilhaResponse> buscar(UUID tenantId, UUID usuarioId) {
        Chave chave = new Chave(tenantId, usuarioId);
        Entrada entrada = entradas.get(chave);
        if (entrada == null) {
            return Optional.empty();
        }
        if (venceu(entrada)) {
            entradas.remove(chave, entrada);
            return Optional.empty();
        }
        return Optional.of(entrada.resposta());
    }

    public void guardar(UUID tenantId, UUID usuarioId, SugestoesDeTrilhaResponse resposta) {
        if (entradas.size() >= MAXIMO_DE_ENTRADAS) {
            // Limpeza grossa de propósito: o cache é só uma economia de chamadas.
            entradas.clear();
        }
        entradas.put(new Chave(tenantId, usuarioId), new Entrada(resposta, Instant.now(clock)));
    }

    /** Esquece a sugestão do usuário — o próximo acesso recalcula. */
    public void invalidar(UUID tenantId, UUID usuarioId) {
        entradas.remove(new Chave(tenantId, usuarioId));
    }

    /**
     * Esquece as sugestões de todos os usuários do tenant. Usado quando muda o
     * catálogo de candidatas (trilha inativada ou reativada), que vale para
     * todos. Os outros tenants não são tocados.
     */
    public void invalidarTenant(UUID tenantId) {
        entradas.keySet().removeIf(chave -> chave.tenantId().equals(tenantId));
    }

    /**
     * Ids das trilhas guardadas, na ordem sugerida. Vazio quando não há nada
     * dentro da validade: a listagem com {@code recomendar=IA} não chama o
     * modelo, só aproveita o que já foi calculado.
     */
    public List<UUID> idsGuardados(UUID tenantId, UUID usuarioId) {
        return buscar(tenantId, usuarioId)
                .map(resposta -> resposta.sugestoes().stream().map(SugestaoTrilhaResponse::id).toList())
                .orElseGet(List::of);
    }

    private boolean venceu(Entrada entrada) {
        return Instant.now(clock).isAfter(entrada.gravadaEm().plus(validade));
    }

    private record Chave(UUID tenantId, UUID usuarioId) {
    }

    private record Entrada(SugestoesDeTrilhaResponse resposta, Instant gravadaEm) {
    }
}
