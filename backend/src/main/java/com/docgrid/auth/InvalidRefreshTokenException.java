package com.docgrid.auth;

import org.springframework.http.HttpStatus;

import com.docgrid.shared.DomainException;

/** O refresh token não existe, expirou, ou já tinha sido usado (revogado). */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Refresh token inválido ou expirado");
    }
}
