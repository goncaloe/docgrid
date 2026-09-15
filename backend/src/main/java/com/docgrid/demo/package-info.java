/**
 * Dados de demonstração: o que o {@code npm run seed} escreve numa base vazia.
 *
 * <p>Todo este pacote é {@code @Profile("demo")} e nada dele existe nos perfis
 * {@code local}, {@code test} ou {@code aws} — a imagem de produção nunca ativa o perfil.
 * Vive em {@code src/main} e não em {@code src/test} por uma razão prática: o
 * {@code spring-boot:run} não vê o classpath de testes, e o seed corre por
 * {@code spring-boot:run -Dspring-boot.run.profiles=local,demo}.
 *
 * <p>O seed entra pelos serviços de domínio — autoriza o upload, põe o PDF no S3 e deixa
 * o worker processá-lo pela fila — e não por SQL. Um documento semeado é indistinguível
 * de um documento real: passou pelo mesmo pipeline, tem os mesmos eventos de auditoria e
 * a mesma confiança por campo. A única exceção é o envelhecimento do histórico
 * ({@code DemoSeedRunner}), que recua datas de criação para o dashboard ter meses e não
 * um lote único.
 */
package com.docgrid.demo;
