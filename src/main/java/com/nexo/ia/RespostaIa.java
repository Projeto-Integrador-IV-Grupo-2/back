package com.nexo.ia;

/**
 * Resposta do modelo.
 *
 * @param texto         texto gerado
 * @param tokensEntrada tokens cobrados pelo prompt (0 quando o provedor não informa)
 * @param tokensSaida   tokens cobrados pela resposta (0 quando o provedor não informa)
 */
public record RespostaIa(String texto, int tokensEntrada, int tokensSaida) {
}
