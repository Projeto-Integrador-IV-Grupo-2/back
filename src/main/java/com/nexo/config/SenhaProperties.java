package com.nexo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Recuperação de senha, lida de {@code app.senha.*} (issue #76).
 *
 * @param tokenValidade  quanto tempo o link vale ({@code SENHA_TOKEN_VALIDADE}, padrão 30 minutos)
 * @param limitePedidos  pedidos aceitos por e-mail e por IP dentro da janela ({@code SENHA_LIMITE_PEDIDOS})
 * @param janelaPedidos  a janela do limite ({@code SENHA_JANELA_PEDIDOS}, padrão 15 minutos)
 */
@ConfigurationProperties(prefix = "app.senha")
public record SenhaProperties(Duration tokenValidade, Integer limitePedidos, Duration janelaPedidos) {

    public SenhaProperties {
        tokenValidade = tokenValidade == null ? Duration.ofMinutes(30) : tokenValidade;
        limitePedidos = limitePedidos == null ? 3 : limitePedidos;
        janelaPedidos = janelaPedidos == null ? Duration.ofMinutes(15) : janelaPedidos;
    }
}
