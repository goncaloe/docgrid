package com.docgrid.support;

/**
 * Dados que um contexto de teste semeia uma vez no arranque e que a limpeza entre
 * classes apaga — a organização e o utilizador da identidade demo, por exemplo.
 *
 * <p>O {@link DatabaseCleanupListener} trunca as tabelas e a seguir chama o {@link #seed()}
 * de cada bean que implemente esta interface, por ordem nenhuma em particular. Sem isto, a
 * limpeza deixaria o contexto a apontar para linhas que já não existem e tudo o que viesse
 * a seguir rebentaria nas chaves estrangeiras.
 *
 * <p>A implementação tem de ser idempotente: é chamada antes de cada classe de teste.
 */
public interface TestDataSeeder {

    void seed();
}
