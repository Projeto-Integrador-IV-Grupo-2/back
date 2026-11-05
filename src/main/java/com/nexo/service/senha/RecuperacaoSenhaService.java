package com.nexo.service.senha;

import com.nexo.config.SenhaProperties;
import com.nexo.entity.PedidoRedefinicaoSenha;
import com.nexo.entity.PedidoRedefinicaoSenha.TipoChave;
import com.nexo.entity.TokenRedefinicaoSenha;
import com.nexo.entity.Usuario;
import com.nexo.event.RedefinicaoSenhaSolicitadaEvent;
import com.nexo.event.SenhaRedefinidaEvent;
import com.nexo.exception.LimiteDePedidosExcedidoException;
import com.nexo.exception.TokenRedefinicaoInvalidoException;
import com.nexo.repository.PedidoRedefinicaoSenhaRepository;
import com.nexo.repository.TenantRepository;
import com.nexo.repository.TokenRedefinicaoSenhaRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.service.senha.GeradorDeToken.TokenGerado;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Esqueci minha senha" e a redefinição pelo link (issue #76).
 *
 * <p><strong>Nada revela quem tem conta:</strong> o pedido responde igual
 * para e-mail cadastrado ou não, conta para o limite do mesmo jeito, e o
 * e-mail sai depois do commit, em outra thread — o tempo de resposta também
 * não muda por causa do envio.</p>
 *
 * <p><strong>Token:</strong> aleatório (256 bits), só o hash no banco, vale
 * por {@code SENHA_TOKEN_VALIDADE} e uma vez. Pedir um link novo apaga os
 * anteriores ainda não usados; redefinir também. Usuário inativo não recebe
 * link e não consegue redefinir.</p>
 *
 * <p><strong>Observer:</strong> o serviço só publica
 * {@link RedefinicaoSenhaSolicitadaEvent} e {@link SenhaRedefinidaEvent}. Quem
 * manda os e-mails e registra no histórico são listeners depois do commit.</p>
 *
 * <p>Singleton do Spring e sem estado.</p>
 */
@Service
public class RecuperacaoSenhaService {

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final TokenRedefinicaoSenhaRepository tokenRepository;
    private final PedidoRedefinicaoSenhaRepository pedidoRepository;
    private final GeradorDeToken gerador;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher publisher;
    private final SenhaProperties properties;
    private final Clock clock;

    public RecuperacaoSenhaService(UsuarioRepository usuarioRepository,
                                   TenantRepository tenantRepository,
                                   TokenRedefinicaoSenhaRepository tokenRepository,
                                   PedidoRedefinicaoSenhaRepository pedidoRepository,
                                   GeradorDeToken gerador,
                                   PasswordEncoder passwordEncoder,
                                   ApplicationEventPublisher publisher,
                                   SenhaProperties properties,
                                   Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.tokenRepository = tokenRepository;
        this.pedidoRepository = pedidoRepository;
        this.gerador = gerador;
        this.passwordEncoder = passwordEncoder;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Gera o link para cada conta ativa com esse e-mail (ou só a da empresa
     * informada) e agenda o envio. Responde sempre igual; passou do limite de
     * pedidos por e-mail ou por IP, 429.
     */
    @Transactional
    public void solicitar(String email, String tenantSubdominio, String ip) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        exigirDentroDoLimite(TipoChave.EMAIL, gerador.hash("email:" + emailNormalizado), agora);
        exigirDentroDoLimite(TipoChave.IP, gerador.hash("ip:" + (ip == null ? "" : ip)), agora);

        for (Usuario usuario : contasAtivas(emailNormalizado, tenantSubdominio)) {
            tokenRepository.apagarNaoUsadosDoUsuario(usuario.getId());
            TokenGerado token = gerador.gerar();
            tokenRepository.save(TokenRedefinicaoSenha.novo(
                    usuario, token.hash(), agora.plus(properties.tokenValidade())));
            publisher.publishEvent(new RedefinicaoSenhaSolicitadaEvent(
                    usuario.getId(), usuario.getEmail(), usuario.getNomeCompleto(), token.valor()));
        }
    }

    /**
     * Troca a senha pelo token do link e marca o token como usado. Token
     * inventado, expirado, já usado ou de usuário inativo: 400, sempre com a
     * mesma mensagem.
     */
    @Transactional
    public void redefinir(String token, String novaSenha) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        TokenRedefinicaoSenha registro = tokenRepository.findByTokenHash(gerador.hash(token))
                .filter(encontrado -> encontrado.valido(agora))
                .filter(encontrado -> encontrado.getUsuario().isAtivo())
                .orElseThrow(TokenRedefinicaoInvalidoException::new);

        Usuario usuario = registro.getUsuario();
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        registro.marcarUsado(agora);
        // Grava o uso antes de apagar os outros links: a exclusão em lote só
        // pega os ainda não usados, e este já precisa constar como usado.
        tokenRepository.saveAndFlush(registro);
        usuarioRepository.saveAndFlush(usuario);
        tokenRepository.apagarNaoUsadosDoUsuario(usuario.getId());

        publisher.publishEvent(new SenhaRedefinidaEvent(usuario.getTenant().getId(), usuario.getId()));
    }

    private List<Usuario> contasAtivas(String email, String tenantSubdominio) {
        if (tenantSubdominio != null && !tenantSubdominio.isBlank()) {
            return tenantRepository.findBySubdominioIgnoreCase(tenantSubdominio.trim().toLowerCase(Locale.ROOT))
                    .flatMap(tenant -> usuarioRepository.findByTenantIdAndEmailIgnoreCase(tenant.getId(), email))
                    .filter(Usuario::isAtivo)
                    .map(List::of)
                    .orElse(List.of());
        }
        return usuarioRepository.findByEmailIgnoreCaseAndAtivoTrue(email);
    }

    /**
     * Conta os pedidos da chave na janela e registra este. A trava por chave
     * faz pedidos simultâneos serem contados um de cada vez.
     */
    private void exigirDentroDoLimite(TipoChave tipo, String chave, OffsetDateTime agora) {
        pedidoRepository.travar(tipo.name() + ":" + chave);
        OffsetDateTime inicioDaJanela = agora.minus(properties.janelaPedidos());
        long recentes = pedidoRepository.countByTipoChaveAndChaveHashAndCriadoEmAfter(tipo, chave, inicioDaJanela);
        if (recentes >= properties.limitePedidos()) {
            throw new LimiteDePedidosExcedidoException(
                    "Muitos pedidos de redefinição de senha. Aguarde alguns minutos e tente de novo.",
                    properties.janelaPedidos().toSeconds());
        }
        pedidoRepository.save(PedidoRedefinicaoSenha.de(tipo, chave, agora));
    }
}
