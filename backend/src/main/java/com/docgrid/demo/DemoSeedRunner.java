package com.docgrid.demo;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.docgrid.auth.AuthService;
import com.docgrid.auth.DemoUserFactory;
import com.docgrid.auth.DemoUserFactory.DemoUser;
import com.docgrid.auth.UserRole;
import com.docgrid.document.Actor;
import com.docgrid.document.DocumentApprovalService;
import com.docgrid.document.DocumentRejectionService;
import com.docgrid.document.DocumentUploadService;
import com.docgrid.document.ExtractedFieldName;
import com.docgrid.document.FieldCorrectionService;
import com.docgrid.document.dto.UploadUrlRequest;
import com.docgrid.document.dto.UploadUrlResponse;
import com.docgrid.export.ExportService;
import com.docgrid.storage.StorageService;

/**
 * O {@code npm run seed}: enche uma base vazia com seis meses de trabalho de uma empresa
 * de demonstração.
 *
 * <p>Tudo o que escreve passa pelos serviços de domínio — autoriza o upload, põe o PDF no
 * S3 e <strong>espera pelo worker</strong>, que consome a fila e processa o documento
 * exatamente como processaria um documento real. Não há atalhos: nenhum {@code insert} em
 * {@code documents}, nenhuma chamada direta ao processador. Um documento semeado tem a
 * mesma auditoria, a mesma validação e a mesma confiança por campo que um documento
 * submetido por uma pessoa — se o pipeline estiver partido, o seed não termina, e isso é
 * uma qualidade e não um defeito.
 *
 * <p>A única exceção é o envelhecimento do histórico ({@link #ageHistory}): recua as datas
 * de criação e dos eventos para instantes coerentes com as datas das faturas. Sem isso, os
 * sessenta documentos nasciam todos no mesmo minuto, o dashboard tinha uma barra só e o
 * tempo médio de revisão media segundos.
 */
@Component
@Profile("demo")
class DemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    static final String ORGANIZATION_NAME = "DocGrid Demonstração, Lda.";
    static final String ORGANIZATION_TAX_ID = "501442889";
    static final String ADMIN_EMAIL = "admin@docgrid.local";
    static final String FINANCE_EMAIL = "finance@docgrid.local";
    static final String MANAGER_EMAIL = "gestor@docgrid.local";
    static final String EMPLOYEE_EMAIL = "joao@docgrid.local";

    /** A mesma semente do catálogo: o histórico também é igual em todas as máquinas. */
    private static final long SEED = 20260915L;

    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    /**
     * A que documentos limpos calha uma rejeição, por ordem de chegada. Duas, mais a do
     * ficheiro reenviado, dão as três rejeições com motivo que o histórico precisa de ter.
     */
    private static final Set<Integer> REJECTED_CLEAN_POSITIONS = Set.of(7, 24);

    private final DemoInvoiceCatalog catalog;
    private final DemoProperties properties;
    private final DemoCurrentUserProvider identity;
    private final AuthService auth;
    private final DemoUserFactory users;
    private final DocumentUploadService uploads;
    private final StorageService storage;
    private final FieldCorrectionService corrections;
    private final DocumentApprovalService approvals;
    private final DocumentRejectionService rejections;
    private final ExportService exports;
    private final JdbcClient jdbc;
    private final ConfigurableApplicationContext context;

    DemoSeedRunner(
            DemoInvoiceCatalog catalog,
            DemoProperties properties,
            DemoCurrentUserProvider identity,
            AuthService auth,
            DemoUserFactory users,
            DocumentUploadService uploads,
            StorageService storage,
            FieldCorrectionService corrections,
            DocumentApprovalService approvals,
            DocumentRejectionService rejections,
            ExportService exports,
            JdbcClient jdbc,
            ConfigurableApplicationContext context) {
        this.catalog = catalog;
        this.properties = properties;
        this.identity = identity;
        this.auth = auth;
        this.users = users;
        this.uploads = uploads;
        this.storage = storage;
        this.corrections = corrections;
        this.approvals = approvals;
        this.rejections = rejections;
        this.exports = exports;
        this.jdbc = jdbc;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.runsOnStartup()) {
            log.info("Perfil demo sem arranque automático: o seed corre quando alguém o pedir.");
            return;
        }
        int code = 0;
        try {
            seed();
        } catch (DemoDataAlreadyExistsException e) {
            log.error(e.getMessage());
            code = 1;
        } catch (RuntimeException e) {
            log.error("O seed falhou: {}", e.getMessage(), e);
            code = 1;
        }
        // O worker está a consumir a fila noutro fio: sem isto o processo ficava vivo e o
        // `npm run seed` nunca voltava à linha de comandos.
        int exitCode = code;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /**
     * Semeia tudo. Lança {@link DemoDataAlreadyExistsException} se já houver dados — nunca
     * escreve por cima: duas execuções seguidas dariam 120 documentos e dois duplicados
     * que ninguém plantou.
     */
    void seed() {
        if (users.find(ADMIN_EMAIL).isPresent()) {
            throw new DemoDataAlreadyExistsException();
        }
        Instant start = Instant.now();
        Random random = new Random(SEED);

        Team team = createTeam();
        log.info("Organização de demonstração criada, com {} utilizadores", 4);

        Map<UUID, DemoInvoice> submitted = submitAll(team);
        log.info("{} documentos processados pelo worker", submitted.size());

        UUID resubmitted = resubmitOneFile(team);
        History history = buildHistory(team, submitted, resubmitted, random);
        log.info(
                "Histórico escrito: {} aprovados, {} rejeitados, {} à espera de revisão",
                history.approved().size(),
                history.rejected().size(),
                history.pending());

        closeTwoOldestPeriods(team, submitted);
        ageHistory(submitted, resubmitted, random);

        log.info(
                "Seed concluído em {} s. Entra em http://localhost:5173 com {} e a password \"{}\".",
                Duration.between(start, Instant.now()).toSeconds(),
                FINANCE_EMAIL,
                properties.password());
    }

    /** A organização, o seu ADMIN (pelo registo) e os três colegas com os outros papéis. */
    private Team createTeam() {
        auth.register(ORGANIZATION_NAME, ORGANIZATION_TAX_ID, ADMIN_EMAIL, properties.password(), "Ana Martins");
        DemoUser admin = users.find(ADMIN_EMAIL).orElseThrow();
        UUID organizationId = admin.organizationId();
        return new Team(
                admin,
                users.create(organizationId, FINANCE_EMAIL, "Sofia Antunes", UserRole.FINANCE, properties.password()),
                users.create(organizationId, MANAGER_EMAIL, "Rui Palma", UserRole.MANAGER, properties.password()),
                users.create(organizationId, EMPLOYEE_EMAIL, "João Freitas", UserRole.EMPLOYEE, properties.password()));
    }

    /**
     * Submete o catálogo inteiro, pela ordem em que ele está, e espera por cada documento.
     *
     * <p>A cópia do duplicado é o único caso com ordem própria: o original tem de estar
     * <em>aprovado</em> antes de ela chegar, porque só uma fatura já aprovada conta como
     * duplicado de negócio. É também o que acontece na vida real — a segunda cópia aparece
     * semanas depois, quando a primeira já foi paga.
     */
    private Map<UUID, DemoInvoice> submitAll(Team team) {
        Map<UUID, DemoInvoice> submitted = new LinkedHashMap<>();
        Map<String, UUID> documentBySlug = new LinkedHashMap<>();

        for (DemoInvoice invoice : catalog.invoices()) {
            if (invoice.demoCase() == DemoCase.DUPLICATE_COPY) {
                approveDuplicateOriginal(team, submitted, documentBySlug);
            }
            UUID documentId = submit(team.employee(), invoice, invoice.slug() + ".pdf");
            awaitProcessed(documentId, invoice.slug());
            requireOurOwnExtraction(documentId, invoice);
            submitted.put(documentId, invoice);
            documentBySlug.put(invoice.slug(), documentId);
            if (submitted.size() % 10 == 0) {
                log.info(
                        "{} de {} documentos processados",
                        submitted.size(),
                        catalog.invoices().size());
            }
        }
        return submitted;
    }

    private void approveDuplicateOriginal(
            Team team, Map<UUID, DemoInvoice> submitted, Map<String, UUID> documentBySlug) {
        submitted.entrySet().stream()
                .filter(entry -> entry.getValue().demoCase() == DemoCase.DUPLICATE_ORIGINAL)
                .findFirst()
                .ifPresent(entry -> approve(team, team.manager(), entry.getKey(), entry.getValue()));
    }

    /**
     * O mesmo ficheiro outra vez — o outro critério da regra de duplicado, o do hash
     * binário, que não precisa de a fatura estar aprovada. Fica um documento a mais no
     * total, e é ele que se rejeita com o motivo "duplicado".
     */
    private UUID resubmitOneFile(Team team) {
        DemoInvoice invoice =
                catalog.invoices().get(Math.min(1, catalog.invoices().size() - 1));
        UUID documentId = submit(team.employee(), invoice, invoice.slug() + "-reenvio.pdf");
        awaitProcessed(documentId, invoice.slug() + " (reenvio)");
        return documentId;
    }

    private UUID submit(DemoUser submitter, DemoInvoice invoice, String filename) {
        identity.actAs(submitter);
        byte[] pdf = InvoicePdfWriter.write(invoice);
        UploadUrlResponse authorized =
                uploads.authorizeUpload(new UploadUrlRequest(filename, "application/pdf", (long) pdf.length));
        // O browser faria o PUT no URL pré-assinado; aqui o ficheiro vai direto ao bucket.
        // O que conta é o resto: a notificação S3→SQS dispara na mesma e é o worker,
        // pela fila, quem processa o documento.
        storage.put(authorized.storageKey(), "application/pdf", pdf);
        return authorized.documentId();
    }

    /** Espera que o worker tire o documento de {@code UPLOADED}/{@code PROCESSING}. */
    private void awaitProcessed(UUID documentId, String label) {
        Instant deadline = Instant.now().plus(properties.waitPerDocument());
        while (Instant.now().isBefore(deadline)) {
            String status = statusOf(documentId);
            if (!"UPLOADED".equals(status) && !"PROCESSING".equals(status)) {
                return;
            }
            sleep();
        }
        throw new IllegalStateException(
                "O documento %s ficou em %s mais de %s. O worker está a correr? O `npm run seed` precisa da fila do LocalStack (`npm run infra`)."
                        .formatted(label, statusOf(documentId), properties.waitPerDocument()));
    }

    /**
     * Confirma que foi o extractor deste processo a ler o documento, e não outro.
     *
     * <p>A fila é partilhada: uma aplicação deixada a correr com {@code npm run up} tem um
     * worker seu, com o {@code StubExtractor} e a sua fixture única, e apanha uma parte das
     * mensagens. Os documentos que ela processa ficam todos com a mesma fatura — a
     * demonstração fica estragada e nada o diz. Mais vale parar aqui, com a razão à vista.
     */
    private void requireOurOwnExtraction(UUID documentId, DemoInvoice invoice) {
        String taxId = jdbc.sql("select supplier_tax_id from documents where id = :id")
                .param("id", documentId)
                .query(String.class)
                .optional()
                .orElse(null);
        if (invoice.fields().supplierTaxId().equals(taxId)) {
            return;
        }
        throw new IllegalStateException(
                ("O documento %s foi processado por outro worker: ficou com o NIF %s em vez de %s. "
                                + "Há outra instância da aplicação a consumir a fila — provavelmente um `npm run up` "
                                + "deixado a correr. Pára-a, corre `npm run down`, depois `npm run seed`, e só então "
                                + "`npm run up`.")
                        .formatted(invoice.slug(), taxId, invoice.fields().supplierTaxId()));
    }

    /**
     * O trabalho de revisão de seis meses: confirmar campos incertos, corrigir o que estava
     * errado, aprovar, rejeitar — e deixar o resto na fila, que é o que a demonstração ao
     * vivo precisa de ter para mostrar.
     */
    private History buildHistory(Team team, Map<UUID, DemoInvoice> submitted, UUID resubmitted, Random random) {
        List<UUID> approved = new ArrayList<>();
        List<UUID> rejected = new ArrayList<>();

        // A rejeição do reenvio: o mesmo ficheiro, apanhado pela regra de duplicado.
        reject(team, team.finance(), resubmitted, "O mesmo ficheiro já tinha sido submetido. Documento duplicado.");
        rejected.add(resubmitted);

        int pending = 0;
        int cleanSoFar = 0;
        for (Map.Entry<UUID, DemoInvoice> entry : submitted.entrySet()) {
            UUID documentId = entry.getKey();
            DemoInvoice invoice = entry.getValue();
            String status = statusOf(documentId);
            if (!"EXTRACTED".equals(status) && !"NEEDS_REVIEW".equals(status)) {
                continue; // já aprovado: é o original do duplicado
            }

            boolean clean = "EXTRACTED".equals(status);
            if (clean) {
                cleanSoFar++;
            }
            // As duas rejeições que não são o duplicado caem em posições fixas: a demonstração
            // tem sempre os mesmos exemplos, e o histórico não fica à mercê de um sorteio.
            Decision decision =
                    REJECTED_CLEAN_POSITIONS.contains(cleanSoFar) ? Decision.REJECT : decide(invoice, clean, random);
            switch (decision) {
                case REJECT -> {
                    reject(team, team.finance(), documentId, rejectionReason(rejected.size()));
                    rejected.add(documentId);
                }
                case CORRECT_AND_APPROVE -> {
                    boolean correctedAmount = correct(team, invoice, documentId);
                    approve(team, correctedAmount ? team.manager() : approver(team, invoice), documentId, invoice);
                    approved.add(documentId);
                }
                case APPROVE -> {
                    approve(team, approver(team, invoice), documentId, invoice);
                    approved.add(documentId);
                }
                case LEAVE_PENDING -> pending++;
            }
        }
        return new History(approved, rejected, pending);
    }

    /**
     * Quem revê decide caso a caso, e é isso que se imita: os documentos limpos aprovam-se
     * quase todos e os que foram para revisão dividem-se entre os que alguém já tratou e os
     * que ainda esperam.
     */
    private Decision decide(DemoInvoice invoice, boolean clean, Random random) {
        if (clean) {
            return random.nextInt(100) < 80 ? Decision.APPROVE : Decision.LEAVE_PENDING;
        }
        return switch (invoice.demoCase()) {
            // O que se corrige e aprova: um campo lido com pouca confiança que o revisor
            // confirma, e uma soma que estava errada e ele acerta.
            case LOW_CONFIDENCE, BAD_ARITHMETIC ->
                random.nextInt(100) < 55 ? Decision.CORRECT_AND_APPROVE : Decision.LEAVE_PENDING;
            // O NIF inválido, o campo em falta e o duplicado ficam para a demonstração:
            // são os três casos que se quer ter para mostrar ao vivo.
            default -> Decision.LEAVE_PENDING;
        };
    }

    /**
     * A correção que o revisor faz: no caso da soma errada, o total certo; nos outros, a
     * confirmação do campo que o motor leu com pouca confiança — que é uma correção a
     * sério, porque marca o campo como escrito por uma pessoa.
     *
     * @return se o campo corrigido foi um montante, caso em que só um gestor pode aprovar
     */
    private boolean correct(Team team, DemoInvoice invoice, UUID documentId) {
        identity.actAs(team.finance());
        Actor actor = Actor.user(team.finance().userId());
        if (invoice.demoCase() == DemoCase.BAD_ARITHMETIC) {
            BigDecimal corrected =
                    invoice.fields().netAmount().add(invoice.fields().vatAmount());
            corrections.correct(
                    documentId,
                    team.organizationId(),
                    ExtractedFieldName.TOTAL_AMOUNT,
                    corrected.toPlainString(),
                    actor);
            return true;
        }
        ExtractedFieldName uncertain = leastConfidentField(invoice);
        corrections.correct(documentId, team.organizationId(), uncertain, invoice.textOf(uncertain), actor);
        return isAmount(uncertain);
    }

    private static ExtractedFieldName leastConfidentField(DemoInvoice invoice) {
        return invoice.confidences().entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(ExtractedFieldName.TOTAL_AMOUNT);
    }

    private static boolean isAmount(ExtractedFieldName field) {
        return field == ExtractedFieldName.NET_AMOUNT
                || field == ExtractedFieldName.VAT_AMOUNT
                || field == ExtractedFieldName.VAT_RATE
                || field == ExtractedFieldName.TOTAL_AMOUNT;
    }

    /** Acima do limite da organização, quem aprova é o gestor — a regra do próprio produto. */
    private DemoUser approver(Team team, DemoInvoice invoice) {
        BigDecimal total = invoice.fields().totalAmount();
        return total != null && total.compareTo(new BigDecimal("1000.00")) > 0 ? team.manager() : team.finance();
    }

    private void approve(Team team, DemoUser approver, UUID documentId, DemoInvoice invoice) {
        identity.actAs(approver);
        approvals.approve(
                documentId,
                team.organizationId(),
                Actor.user(approver.userId()),
                approver.role(),
                invoice.suggestedCategory());
    }

    private void reject(Team team, DemoUser reviewer, UUID documentId, String reason) {
        identity.actAs(reviewer);
        rejections.reject(documentId, team.organizationId(), Actor.user(reviewer.userId()), reason);
    }

    private static String rejectionReason(int alreadyRejected) {
        return alreadyRejected == 1
                ? "Não é uma fatura: é um recibo de pagamento já registado noutro documento."
                : "Fatura anulada pelo fornecedor e substituída por uma nota de crédito.";
    }

    /**
     * Fecha os dois meses mais antigos com faturas. O fecho leva todos os aprovados até ao
     * fim do período, por isso o mais antigo vai primeiro — e os documentos que ele leva
     * ficam em {@code EXPORTED}, com o CSV no S3.
     */
    private void closeTwoOldestPeriods(Team team, Map<UUID, DemoInvoice> submitted) {
        identity.actAs(team.admin());
        List<YearMonth> periods = submitted.values().stream()
                .map(invoice -> invoice.fields().issueDate())
                .filter(date -> date != null)
                .map(YearMonth::from)
                .distinct()
                .sorted()
                .limit(2)
                .toList();
        for (YearMonth period : periods) {
            exports.create(
                    team.organizationId(), period, Actor.user(team.admin().userId()));
            log.info("Período {} fechado", period);
        }
    }

    /**
     * Recua as datas para o histórico parecer histórico: cada documento nasce dois dias
     * depois da data da sua fatura, a extração leva segundos e a decisão do revisor chega
     * horas ou dias depois — que é o que o dashboard mede como tempo médio de revisão.
     *
     * <p>É a única escrita em SQL do seed, e está confinada a este método. Não há forma de
     * a fazer pelo domínio: as datas de auditoria são, por desenho, escritas pelo sistema
     * e não recebidas de fora.
     */
    private void ageHistory(Map<UUID, DemoInvoice> submitted, UUID resubmitted, Random random) {
        Map<UUID, DemoInvoice> all = new LinkedHashMap<>(submitted);
        all.put(
                resubmitted,
                catalog.invoices().get(Math.min(1, catalog.invoices().size() - 1)));

        for (Map.Entry<UUID, DemoInvoice> entry : all.entrySet()) {
            UUID documentId = entry.getKey();
            LocalDate issueDate = entry.getValue().fields().issueDate();
            Instant submittedAt = submissionInstant(issueDate, random);
            Duration reviewDelay = Duration.ofMinutes(35L + random.nextInt(3 * 24 * 60));

            List<Long> events = jdbc.sql(
                            "select id from document_events where document_id = :id order by occurred_at, id")
                    .param("id", documentId)
                    .query(Long.class)
                    .list();

            Instant last = submittedAt;
            for (int i = 0; i < events.size(); i++) {
                Instant occurredAt = instantOfEvent(submittedAt, reviewDelay, i);
                jdbc.sql("update document_events set occurred_at = :at where id = :id")
                        .param("at", occurredAt.atOffset(ZoneOffset.UTC))
                        .param("id", events.get(i))
                        .update();
                last = occurredAt;
            }
            jdbc.sql("update documents set created_at = :created, updated_at = :updated where id = :id")
                    .param("created", submittedAt.atOffset(ZoneOffset.UTC))
                    .param("updated", last.atOffset(ZoneOffset.UTC))
                    .param("id", documentId)
                    .update();
        }
        log.info("Histórico envelhecido: {} documentos espalhados por seis meses", all.size());
    }

    /** Dois dias depois da fatura, a meio da manhã. Sem data de emissão, há dois meses. */
    private Instant submissionInstant(LocalDate issueDate, Random random) {
        LocalDate base = (issueDate == null ? LocalDate.now().minusMonths(2) : issueDate).plusDays(2);
        return base.atTime(LocalTime.of(9 + random.nextInt(8), random.nextInt(60)))
                .toInstant(ZoneOffset.UTC);
    }

    /**
     * O instante de cada evento: os três primeiros são a máquina a trabalhar (segundos), e
     * a partir daí é uma pessoa — a revisão, a correção, a decisão, a exportação.
     */
    private static Instant instantOfEvent(Instant submittedAt, Duration reviewDelay, int index) {
        return switch (index) {
            case 0 -> submittedAt;
            case 1 -> submittedAt.plusSeconds(3);
            case 2 -> submittedAt.plusSeconds(11);
            default -> submittedAt.plus(reviewDelay).plusSeconds((index - 3L) * 95L);
        };
    }

    private String statusOf(UUID documentId) {
        return jdbc.sql("select status from documents where id = :id")
                .param("id", documentId)
                .query(String.class)
                .single();
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Seed interrompido", e);
        }
    }

    /** Quem trabalha na organização de demonstração. */
    private record Team(DemoUser admin, DemoUser finance, DemoUser manager, DemoUser employee) {

        UUID organizationId() {
            return admin.organizationId();
        }
    }

    private record History(List<UUID> approved, List<UUID> rejected, int pending) {}

    private enum Decision {
        APPROVE,
        CORRECT_AND_APPROVE,
        REJECT,
        LEAVE_PENDING
    }

    /** Já há dados semeados: o seed não escreve por cima de nada. */
    static class DemoDataAlreadyExistsException extends RuntimeException {

        DemoDataAlreadyExistsException() {
            super("Já há dados de demonstração nesta base. Corre `npm run down` e volta a tentar.");
        }
    }
}
