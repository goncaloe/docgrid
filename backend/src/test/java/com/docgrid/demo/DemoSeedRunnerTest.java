package com.docgrid.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import com.docgrid.support.LocalStackPipelineConfiguration;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * O seed de ponta a ponta, em ponto pequeno: quatro faturas, o Postgres e o LocalStack
 * verdadeiros, e o worker a consumir a fila como consome em local.
 *
 * <p>É o teste que impede a demonstração de partir em silêncio. Se o marcador do PDF
 * deixar de ser lido, se a notificação S3→SQS deixar de chegar, se uma regra de validação
 * mudar de ideias sobre estes dados — o seed deixa de terminar, e é aqui que se vê, e não
 * cinco minutos antes de gravar o vídeo.
 *
 * <p>O seed corre à mão e não no arranque ({@code docgrid.demo.auto-run=false}): a limpeza
 * da base entre classes de teste passa depois de o contexto subir, e levaria com ela tudo
 * o que um seed automático tivesse semeado.
 */
@SpringBootTest(
        properties = {"docgrid.demo.count=4", "docgrid.demo.auto-run=false", "docgrid.demo.wait-per-document=60s"})
@ActiveProfiles({"test", "demo", "worker"})
@Import({PostgresContainerConfiguration.class, LocalStackPipelineConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoSeedRunnerTest {

    @Autowired
    private DemoSeedRunner runner;

    @Autowired
    private JdbcClient jdbc;

    /** Semear é caro: faz-se uma vez e todos os testes da classe leem o que ele escreveu. */
    @BeforeAll
    void seedOnce() {
        runner.seed();
    }

    @Test
    @DisplayName("os documentos chegam todos a um estado final, pelo pipeline verdadeiro")
    void everyDocumentReachesAFinalState() {
        List<String> statuses =
                jdbc.sql("select status from documents").query(String.class).list();

        // Quatro faturas do catálogo mais o ficheiro reenviado, que existe para mostrar a
        // deteção de duplicado binário.
        assertThat(statuses).hasSize(5);
        assertThat(statuses).doesNotContain("UPLOADED", "PROCESSING", "FAILED");
    }

    @Test
    @DisplayName("cada documento tem auditoria: quem o criou e cada transição de estado")
    void writesAnAuditTrail() {
        Long documentsWithoutEvents = jdbc.sql("""
                        select count(*) from documents d
                        where not exists (select 1 from document_events e where e.document_id = d.id)
                        """).query(Long.class).single();
        Long createdEvents = jdbc.sql("select count(*) from document_events where event_type = 'CREATED'")
                .query(Long.class)
                .single();

        assertThat(documentsWithoutEvents).isZero();
        assertThat(createdEvents).isEqualTo(5);
    }

    @Test
    @DisplayName("os campos extraídos chegam com a confiança da máquina")
    void keepsConfidencePerField() {
        Long machineFieldsWithoutConfidence = jdbc.sql(
                        "select count(*) from extracted_fields where source = 'AI' and confidence is null")
                .query(Long.class)
                .single();
        Long fields = jdbc.sql("select count(*) from extracted_fields")
                .query(Long.class)
                .single();

        assertThat(machineFieldsWithoutConfidence).isZero();
        assertThat(fields).isGreaterThan(20L);
    }

    @Test
    @DisplayName("o histórico é envelhecido: os documentos não nascem todos no mesmo minuto")
    void agesTheHistory() {
        Long sameDay = jdbc.sql("select count(distinct date(created_at)) from documents")
                .query(Long.class)
                .single();

        assertThat(sameDay).isGreaterThan(1L);
    }

    @Test
    @DisplayName("a equipa de demonstração fica criada, com os quatro papéis")
    void createsTheTeam() {
        List<String> roles = jdbc.sql("select role from users order by role")
                .query(String.class)
                .list();

        assertThat(roles).containsExactlyInAnyOrder("ADMIN", "FINANCE", "MANAGER", "EMPLOYEE");
    }

    @Test
    @DisplayName("correr o seed outra vez aborta em vez de duplicar tudo")
    void refusesToSeedTwice() {
        Long documentsBefore =
                jdbc.sql("select count(*) from documents").query(Long.class).single();

        assertThatThrownBy(() -> runner.seed())
                .isInstanceOf(DemoSeedRunner.DemoDataAlreadyExistsException.class)
                .hasMessageContaining("npm run down");

        assertThat(jdbc.sql("select count(*) from documents").query(Long.class).single())
                .isEqualTo(documentsBefore);
    }
}
