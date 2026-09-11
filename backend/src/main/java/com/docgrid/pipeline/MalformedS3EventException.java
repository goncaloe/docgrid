package com.docgrid.pipeline;

import org.springframework.http.HttpStatus;

import com.docgrid.shared.DomainException;

/**
 * A mensagem não é um evento de notificação do S3 que o pipeline saiba ler.
 *
 * <p>Erro permanente: a mensagem nunca vai passar a ser legível, por isso o worker não
 * a apaga — deixa-a esgotar as entregas e chegar à dead-letter queue, onde alguém a pode
 * inspecionar. Apagá-la de imediato seria esconder o problema em vez de o tratar. Nunca
 * atravessa a fronteira de um controller REST, mas herda de {@code DomainException} como
 * todo o resto.
 */
public class MalformedS3EventException extends DomainException {

    private static final String PREFIX = "Evento do S3 ilegível: ";

    /** A mensagem de erro não repete o corpo da mensagem, só o seu início. */
    public MalformedS3EventException(String body) {
        super(HttpStatus.BAD_REQUEST, PREFIX + preview(body));
    }

    private static String preview(String body) {
        if (body == null) {
            return "vazio";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "…";
    }
}
