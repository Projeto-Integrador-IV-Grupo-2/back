package com.nexo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.config.JwtProperties;
import com.nexo.entity.Perfil;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "nexo-chave-de-teste-automatizado-nao-use-em-producao";

    @Mock
    private CustomUserDetailsService userDetailsService;

    private final JwtService jwtService = new JwtService(
            new JwtProperties(SECRET, "nexo-api", Duration.ofHours(2)));
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoraTokenEmitidoAntesDaUltimaTrocaDeSenha() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UsuarioAutenticado donoDoToken = usuario(usuarioId, 0);
        UsuarioAutenticado atualizado = usuario(usuarioId, 1);
        when(userDetailsService.loadUserById(usuarioId)).thenReturn(atualizado);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.gerar(donoDoToken).valor());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void autenticaTokenDaVersaoAtualDaSenha() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UsuarioAutenticado atualizado = usuario(usuarioId, 1);
        when(userDetailsService.loadUserById(usuarioId)).thenReturn(atualizado);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.gerar(atualizado).valor());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNotNull()
                .extracting("principal")
                .isEqualTo(atualizado);
        verify(chain).doFilter(request, response);
    }

    private static UsuarioAutenticado usuario(UUID id, int versaoSenha) {
        return new UsuarioAutenticado(
                id,
                UUID.randomUUID(),
                "Pessoa",
                "pessoa@nexo.com",
                "hash",
                Perfil.COLABORADOR,
                true,
                versaoSenha);
    }
}
