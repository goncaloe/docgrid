package com.docgrid.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Sessão sem estado (o access token traz tudo; nada em servidor). Autorização por papel
 * vive em {@code @PreAuthorize}, método a método — ver {@code docs/03-CONVENTIONS.md} sobre
 * organizar por funcionalidade, não por camada: um {@code SecurityConfig} central só decide
 * o que é público, o resto decide-se onde a operação vive.
 *
 * <p>Um pedido sem token válido nunca chega a um controller — falha aqui, com o mesmo
 * formato RFC 7807 que {@code GlobalExceptionHandler} usa para tudo o resto, porque o
 * {@code @RestControllerAdvice} só intercepta exceções que aconteçam dentro do
 * {@code DispatcherServlet}, e isto acontece antes.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/api/auth/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        // /actuator/health/** (grupos liveness/readiness e componentes) é público: as
        // probes de container e orquestração não podem autenticar. Os detalhes ficam
        // protegidos por `show-details: when-authorized`; o resto do actuator não.
        "/actuator/health/**",
        "/actuator/info"
    };

    private final ObjectMapper objectMapper;

    SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        // O health e o info continuam públicos (foram avaliados acima);
                        // o resto do actuator — métricas e estado interno do processo —
                        // é território de administração.
                        .requestMatchers("/actuator/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint((request, response, exception) ->
                                writeProblem(response, HttpStatus.UNAUTHORIZED, "Autenticação necessária"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(response, HttpStatus.FORBIDDEN, "Sem permissão para esta operação")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ProblemDetail.forStatusAndDetail(status, detail));
    }
}
