package com.docgrid.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Tradução única de todas as exceções para RFC 7807 ({@code application/problem+json}).
 *
 * <p>{@link DomainException} já sabe o seu próprio {@link HttpStatus}: este tratador nunca
 * conhece um caso concreto, só a hierarquia. As exceções de segurança que acontecem dentro
 * do próprio controller (autorização por papel, com {@code @PreAuthorize}) chegam aqui como
 * qualquer outra; as que acontecem antes de o pedido alcançar um controller (sem token, ou
 * com um token inválido) são tratadas pelo filtro de segurança — ver
 * {@code com.docgrid.auth.SecurityConfig}.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail onDomainException(DomainException exception) {
        return ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail onAccessDenied(AccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Sem permissão para esta operação");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail onAuthentication(AuthenticationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Autenticação necessária");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .reduce((a, b) -> a + "; " + b)
                .orElse("Pedido inválido");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Valor inválido para \"%s\": %s".formatted(exception.getName(), exception.getValue()));
    }

    /**
     * O cliente continua a receber só "Erro interno" — nada do que correu mal por dentro
     * atravessa a fronteira. Mas fica registado do lado do servidor: sem isto, um 500 não
     * deixava rasto nenhum no log e a única forma de saber a causa era reproduzi-lo com o
     * depurador ligado.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail onUnexpected(Exception exception) {
        log.error("Erro não tratado a servir o pedido", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");
    }
}
