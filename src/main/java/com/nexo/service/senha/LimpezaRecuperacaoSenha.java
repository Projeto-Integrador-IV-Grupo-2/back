package com.nexo.service.senha;

import com.nexo.config.ClockConfig;
import com.nexo.config.SenhaProperties;
import com.nexo.repository.PedidoRedefinicaoSenhaRepository;
import com.nexo.repository.TokenRedefinicaoSenhaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limpeza diária da recuperação de senha (issue #76): apaga os links vencidos
 * há mais de um dia e os pedidos que já saíram da janela do limite. Nenhum
 * deles serve para mais nada depois disso.
 */
@Component
public class LimpezaRecuperacaoSenha {

    private static final Logger log = LoggerFactory.getLogger(LimpezaRecuperacaoSenha.class);

    private final TokenRedefinicaoSenhaRepository tokenRepository;
    private final PedidoRedefinicaoSenhaRepository pedidoRepository;
    private final SenhaProperties properties;
    private final Clock clock;

    public LimpezaRecuperacaoSenha(TokenRedefinicaoSenhaRepository tokenRepository,
                                   PedidoRedefinicaoSenhaRepository pedidoRepository,
                                   SenhaProperties properties,
                                   Clock clock) {
        this.tokenRepository = tokenRepository;
        this.pedidoRepository = pedidoRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = ClockConfig.FUSO_ID)
    @Transactional
    public void limpar() {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        int tokens = tokenRepository.apagarExpiradosAntesDe(agora.minus(Duration.ofDays(1)));
        int pedidos = pedidoRepository.apagarAntesDe(agora.minus(properties.janelaPedidos()));
        if (tokens + pedidos > 0) {
            log.info("Limpeza da recuperação de senha: {} links e {} pedidos antigos apagados", tokens, pedidos);
        }
    }
}
