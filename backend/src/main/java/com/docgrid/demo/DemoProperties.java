package com.docgrid.demo;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Os parâmetros do seed. Os valores da demonstração estão em
 * {@code application-demo.yml}; os que estão aqui são a rede de segurança para quando
 * alguém corre o perfil sem esse ficheiro.
 *
 * @param count quantas faturas semear; 60 na demonstração, 4 no teste de integração
 * @param password a password comum aos utilizadores semeados — vale só para o LocalStack
 *     desta máquina, e está escrita no README de propósito
 * @param waitPerDocument quanto se espera que o worker termine cada documento antes de
 *     desistir com uma mensagem clara
 */
@ConfigurationProperties("docgrid.demo")
public record DemoProperties(int count, String password, Duration waitPerDocument) {

    public DemoProperties {
        count = count <= 0 ? 60 : count;
        password = password == null || password.isBlank() ? "docgrid-demo" : password;
        waitPerDocument = waitPerDocument == null ? Duration.ofSeconds(60) : waitPerDocument;
    }
}
