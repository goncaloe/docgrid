package com.docgrid.export;

import java.time.YearMonth;

import org.springframework.http.HttpStatus;

import com.docgrid.shared.DomainException;

/**
 * Lançada quando se tenta exportar um período ainda em curso ou futuro.
 *
 * <p>O mês corrente só pode ser exportado depois de terminar: as faturas podem continuar
 * a chegar até ao último dia do mês.
 */
class PeriodNotClosableException extends DomainException {

    PeriodNotClosableException(YearMonth period) {
        super(HttpStatus.CONFLICT, "O período %s ainda não terminou".formatted(period));
    }
}
