package com.docgrid.auth;

import org.springframework.http.HttpStatus;

import com.docgrid.shared.DomainException;

/** Já existe um utilizador com este email — o email identifica a pessoa em todo o sistema. */
public class EmailAlreadyRegisteredException extends DomainException {

    public EmailAlreadyRegisteredException(String email) {
        super(HttpStatus.CONFLICT, "Já existe uma conta com o email " + email);
    }
}
