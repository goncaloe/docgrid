package com.docgrid.auth.dto;

import java.time.Instant;

/**
 * @param accessTokenExpiresAt até quando o {@code accessToken} é válido — o cliente sabe
 *     quando pedir um refresh sem esperar por um 401
 */
public record TokenResponse(String accessToken, Instant accessTokenExpiresAt, String refreshToken) {}
