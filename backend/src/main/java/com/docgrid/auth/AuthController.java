package com.docgrid.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.docgrid.auth.dto.LoginRequest;
import com.docgrid.auth.dto.RefreshRequest;
import com.docgrid.auth.dto.RegisterRequest;
import com.docgrid.auth.dto.TokenResponse;

/**
 * Autenticação. Sem {@code @PreAuthorize}: estes quatro endpoints são os únicos que um
 * pedido sem token pode alcançar — ver {@link SecurityConfig}.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(
                request.organizationName(),
                request.organizationTaxId(),
                request.adminEmail(),
                request.adminPassword(),
                request.adminFullName());
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
    }
}
