package com.docgrid.support;

import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Esvazia a base de dados antes de cada classe de teste, para que nenhuma classe herde as
 * linhas de outra.
 *
 * <p>O Postgres dos testes é um só por contexto Spring, e o Spring reaproveita o contexto
 * entre classes com a mesma configuração — ver {@link PostgresContainerConfiguration}. As
 * classes que não são {@code @Transactional} fazem commit, por razão: exercem fluxos que
 * atravessam fronteiras de transação (o worker noutra thread, a cadeia de filtros real),
 * e uma transação de teste não seria a transação que o código sob teste usa. O que elas
 * escrevem fica, portanto, e acumula-se ao longo da execução.
 *
 * <p>Isso não incomoda quem procura pelo id que acabou de criar, mas falseia qualquer
 * pergunta agregada — "quantos documentos tem esta organização?". E como a ordem das
 * classes é a do sistema de ficheiros (alfabética em NTFS, por hash em ext4), o mesmo
 * teste passava em Windows e falhava no Linux. Ver
 * {@code docs/adr/0020-isolamento-de-dados-nos-testes.md}.
 *
 * <p>Regista-se em {@code META-INF/spring.factories}, por isso aplica-se a todos os testes
 * com contexto Spring sem que nenhum tenha de o pedir. É deliberado: "nenhuma classe herda
 * as linhas de outra" é uma regra da suíte inteira, não uma opção por teste.
 */
public class DatabaseCleanupListener implements TestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCleanupListener.class);

    /** O Flyway precisa do seu histórico; truncá-lo faria as migrações correr outra vez. */
    private static final String FLYWAY_HISTORY = "flyway_schema_history";

    @Override
    public void beforeTestClass(TestContext testContext) {
        ApplicationContext context = testContext.getApplicationContext();
        DataSource dataSource = context.getBeanProvider(DataSource.class).getIfAvailable();
        if (dataSource == null) {
            return;
        }

        truncateAll(new JdbcTemplate(dataSource));
        context.getBeanProvider(TestDataSeeder.class).forEach(TestDataSeeder::seed);
    }

    private void truncateAll(JdbcTemplate jdbc) {
        List<String> tables = jdbc.queryForList(
                "select tablename from pg_tables where schemaname = 'public' and tablename <> ?",
                String.class,
                FLYWAY_HISTORY);

        if (tables.isEmpty()) {
            log.warn("Nenhuma tabela para limpar - as migrações chegaram a correr?");
            return;
        }

        // Uma só instrução: o CASCADE resolve a ordem das chaves estrangeiras por nós.
        String quoted = tables.stream().map(t -> "\"" + t + "\"").collect(Collectors.joining(", "));
        jdbc.execute("truncate table " + quoted + " restart identity cascade");
    }
}
