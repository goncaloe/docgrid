package com.docgrid.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Teste de persistência contra um Postgres real, com as migrações do Flyway aplicadas.
 *
 * <p>O {@code replace = NONE} não é decorativo: sem ele o Spring troca o container por uma
 * base embutida e o teste deixa de provar o que diz provar. Com o {@code ddl-auto} em
 * {@code validate}, qualquer divergência entre entidades e migrações rebenta aqui.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
public @interface RepositoryTest {}
