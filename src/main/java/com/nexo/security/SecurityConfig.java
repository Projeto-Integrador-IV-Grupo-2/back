package com.nexo.security;

import com.nexo.config.JwtProperties;
import com.nexo.entity.Perfil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Configuração de segurança da API.
 *
 * <p>A API é stateless: não há sessão nem formulário de login — cada requisição
 * se identifica pelo token JWT, validado pelo {@link JwtAuthenticationFilter}
 * antes da autenticação padrão do Spring Security. Continuam públicos apenas os
 * endpoints de infraestrutura e o login; todo o restante exige token, e as rotas
 * administrativas exigem também o perfil ADMINISTRADOR.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String LOGIN = "/api/auth/login";

    private static final String[] ENDPOINTS_PUBLICOS = {
            "/api/health",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/error"
    };

    /** Prefixo reservado às rotas administrativas: só o perfil ADMINISTRADOR entra. */
    private static final String ENDPOINTS_ADMINISTRATIVOS = "/api/admin/**";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   SecurityErrorHandler securityErrorHandler,
                                                   JwtService jwtService,
                                                   CustomUserDetailsService userDetailsService) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, LOGIN).permitAll()
                        .requestMatchers(ENDPOINTS_PUBLICOS).permitAll()
                        .requestMatchers(ENDPOINTS_ADMINISTRATIVOS).hasRole(Perfil.ADMINISTRADOR.name())
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * BCrypt para gravar e conferir senhas — o formato exigido pela constraint
     * {@code ck_usuario_senha_bcrypt} da tabela {@code usuario}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
