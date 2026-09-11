package com.docgrid.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do par de tokens. O segredo nunca tem valor por omissão — chega sempre por
 * variável de ambiente; sem ele, a aplicação não arranca (regra 4 do {@code AGENTS.md}).
 *
 * @param secret a chave HMAC que assina o access token
 * @param accessTokenTtl validade do access token, stateless — curta de propósito
 * @param refreshTokenTtl validade do refresh token, opaco e revogável em BD
 */
@ConfigurationProperties("docgrid.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}
