package com.docgrid.auth;

import org.springframework.http.HttpStatus;

import com.docgrid.shared.DomainException;

/**
 * Email ou password errados. Uma só mensagem para os dois casos: dizer qual dos dois falhou
 * confirmaria a um atacante que o email existe.
 */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Email ou password inválidos");
    }
}
