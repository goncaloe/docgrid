package com.docgrid.document;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.docgrid.extraction.DocumentExtractor;
import com.docgrid.extraction.ExtractionResult;
import com.docgrid.extraction.UnreadableDocumentException;
import com.docgrid.storage.NoSuchObjectException;
import com.docgrid.storage.StorageProperties;
import com.docgrid.storage.StorageService;
import com.docgrid.validation.ValidationEngine;
import com.docgrid.validation.ValidationSummary;

/**
 * O processamento de uma entrega vinda da fila: reclamar o documento idempotentemente,
 * ler o objeto, extrair os campos e concluir.
 *
 * <p>As transações são explícitas ({@link TransactionTemplate}, e não
 * {@code @Transactional}) porque precisam de ser duas: primeiro a que reclama — insere o
 * {@link ProcessingClaim} e passa o documento a {@code PROCESSING} na mesma transação,
 * portanto quem ganha o claim é quem processa; depois, já sem a transação aberta, o
 * trabalho em si — ler o S3, extrair — que não deve segurar uma ligação à base de dados;
 * e por fim a transação curta que escreve tudo o que o trabalho produziu. Com
 * {@code @Transactional} por método não havia forma de desenhar isto sem três beans.
 *
 * <p>A idempotência é o conjunto do claim (a chave do S3 é primária: duas entregas
 * simultâneas, um vencedor) e do estado do documento (ver
 * {@code docs/adr/0007-idempotencia-do-worker.md}). O SQS garante at-least-once, nunca
 * exactly-once — a mesma mensagem chega repetida, e o sistema tem de não se importar.
 */
@Service
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final DocumentRepository documents;
    private final DocumentEventRepository events;
    private final ProcessingClaimRepository claims;
    private final StorageService storage;
    private final DocumentExtractor extractor;
    private final ValidationEngine validationEngine;
    private final DocumentValidationContextFactory validationContexts;
    private final TransactionTemplate transactions;
    private final String bucket;

    public DocumentProcessor(
            DocumentRepository documents,
            DocumentEventRepository events,
            ProcessingClaimRepository claims,
            StorageService storage,
            DocumentExtractor extractor,
            ValidationEngine validationEngine,
            DocumentValidationContextFactory validationContexts,
            TransactionTemplate transactions,
            StorageProperties storageProperties) {
        this.documents = documents;
        this.events = events;
        this.claims = claims;
        this.storage = storage;
        this.extractor = extractor;
        this.validationEngine = validationEngine;
        this.validationContexts = validationContexts;
        this.transactions = transactions;
        this.bucket = storageProperties.bucket();
    }

    /**
     * Reabre um documento {@code FAILED} para uma nova tentativa — o reprocessamento
     * manual do ciclo de vida, acionado a partir da DLQ. Passa o documento a
     * {@code PROCESSING} e reabre o claim, para que a entrega seguinte da mensagem retome
     * o trabalho em vez de o tratar como duplicado.
     *
     * <p>Só age sobre {@code FAILED}: um documento noutro estado ou não precisa de ajuda
     * (já foi extraído, já foi rejeitado) ou já está a ser tratado.
     *
     * @return {@code true} se o documento foi reaberto; {@code false} se não existe ou
     *     não estava {@code FAILED}
     */
    public boolean reopenForReprocessing(String storageKey) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            Document document = documents.findByStorageKey(storageKey).orElse(null);
            if (document == null || document.getStatus() != DocumentStatus.FAILED) {
                return false;
            }
            events.save(document.transitionTo(
                    DocumentStatus.PROCESSING, Actor.system(), "Reprocessamento a partir da dead-letter queue"));
            claims.findById(storageKey).ifPresent(ProcessingClaim::reopen);
            log.info("Documento {} reaberto para reprocessamento", document.getId());
            return true;
        }));
    }

    /**
     * Processa a entrega de um evento {@code ObjectCreated} desta chave do S3.
     *
     * @param finalAttempt verdadeiro se esta é a última entrega antes da mensagem ir para
     *     a dead-letter queue — a última hipótese de deixar o documento num estado com
     *     significado
     * @return se a mensagem pode ser apagada ({@code PROCESSED}, {@code DUPLICATE}) ou
     *     se deve ficar na fila ({@code FAILED})
     */
    public ProcessingOutcome process(String bucket, String storageKey, boolean finalAttempt) {
        if (!this.bucket.equals(bucket)) {
            log.warn("Evento de um bucket desconhecido ({}) e chave {} — ignorado", bucket, storageKey);
            return ProcessingOutcome.FAILED;
        }

        Claim claim = claim(storageKey);
        switch (claim.decision()) {
            case NOT_FOUND -> {
                log.warn("Evento sem documento: a chave {} não tem registo. Mensagem segue para a DLQ", storageKey);
                return ProcessingOutcome.FAILED;
            }
            case ALREADY_DONE -> {
                log.info("Chave {} já processada — entrega duplicada ignorada", storageKey);
                return ProcessingOutcome.DUPLICATE;
            }
            case LEAVE -> {
                log.info(
                        "Documento {} já falhou antes; mensagem da chave {} segue para a DLQ",
                        claim.documentId(),
                        storageKey);
                return ProcessingOutcome.FAILED;
            }
            case PROCEED, RESUME -> {}
        }

        MDC.put("documentId", String.valueOf(claim.documentId()));
        try {
            byte[] content = storage.download(storageKey).content();
            ExtractionResult result = extractor.extract(content, claim.contentType());
            complete(claim.documentId(), storageKey, content, result);

            log.info("Documento {} extraído: a chave {} está pronta para revisão", claim.documentId(), storageKey);
            return ProcessingOutcome.PROCESSED;
        } catch (NoSuchObjectException e) {
            // O S3 é fortemente consistente: um objeto que não existe depois de um
            // ObjectCreated não vai passar a existir. Erro permanente, sem retry.
            log.warn("Documento {}: {}", claim.documentId(), e.getMessage());
            fail(claim.documentId(), "O objeto não existe no S3: " + storageKey);
            return ProcessingOutcome.FAILED;
        } catch (UnreadableDocumentException e) {
            // Ilegível é permanente: repetir a chamada não torna a foto menos tremida.
            // Falha logo, sem esperar pelas tentativas do SQS — quem tem de intervir é
            // uma pessoa, na revisão, não um retry.
            log.warn("Documento {}: {}", claim.documentId(), e.getMessage());
            fail(claim.documentId(), "Documento ilegível: " + e.getMessage());
            return ProcessingOutcome.FAILED;
        } catch (InvalidStatusTransitionException e) {
            // Outra entrega concluiu o trabalho primeiro: a nossa escrita rolou toda
            // para trás, na transação dela nada. É o duplicado a resolver-se a si.
            log.info("Documento {} já tinha sido concluído por outra entrega", claim.documentId());
            return ProcessingOutcome.DUPLICATE;
        } catch (RuntimeException e) {
            log.warn("Documento {}: falha transitória a processar a chave {}", claim.documentId(), storageKey, e);
            if (finalAttempt) {
                fail(claim.documentId(), "Falhou todas as tentativas de processamento: " + e.getMessage());
            }
            return ProcessingOutcome.FAILED;
        } finally {
            MDC.remove("documentId");
        }
    }

    /**
     * Reclama o documento para esta entrega, na transação que insere o claim. Três
     * resultados: nunca visto (insere o claim e passa a {@code PROCESSING}), já visto e
     * concluído (nada a fazer), já visto e incompleto (retomar, ou deixar seguir para a
     * DLQ se o documento já tiver falhado).
     */
    private Claim claim(String storageKey) {
        try {
            return transactions.execute(status -> {
                Document document = documents.findByStorageKey(storageKey).orElse(null);
                if (document == null) {
                    return new Claim(Decision.NOT_FOUND, null, null);
                }
                if (document.getStatus() == DocumentStatus.UPLOADED) {
                    try {
                        claims.saveAndFlush(new ProcessingClaim(document.getId(), storageKey));
                    } catch (DataIntegrityViolationException raced) {
                        // Outra entrega inseriu o claim primeiro: a nossa transação não
                        // tem salvação, e a decisão é relida de seguida sobre o que ficou.
                        throw new DuplicateClaimException();
                    }
                    events.save(document.transitionTo(DocumentStatus.PROCESSING, Actor.system(), null));
                    log.info("Documento {} reclamado em PROCESSING", document.getId());
                    return new Claim(Decision.PROCEED, document.getId(), document.getContentType());
                }
                return decisionForClaimed(storageKey, document);
            });
        } catch (DuplicateClaimException raced) {
            return transactions.execute(status -> {
                Document document = documents.findByStorageKey(storageKey).orElse(null);
                return document == null
                        ? new Claim(Decision.NOT_FOUND, null, null)
                        : decisionForClaimed(storageKey, document);
            });
        }
    }

    /**
     * A decisão para uma entrega cuja chave já tem claim: concluído é duplicado
     * verdadeiro; {@code PROCESSING} é uma tentativa interrompida que retoma;
     * {@code FAILED} é um documento que já desistiu — a mensagem segue para a DLQ.
     */
    private Claim decisionForClaimed(String storageKey, Document document) {
        ProcessingClaim claim = claims.findById(storageKey).orElse(null);
        if (claim == null || claim.isCompleted()) {
            return new Claim(Decision.ALREADY_DONE, document.getId(), document.getContentType());
        }
        return switch (document.getStatus()) {
            case PROCESSING -> new Claim(Decision.RESUME, document.getId(), document.getContentType());
            case FAILED -> new Claim(Decision.LEAVE, document.getId(), document.getContentType());
            default -> new Claim(Decision.ALREADY_DONE, document.getId(), document.getContentType());
        };
    }

    /**
     * A transação que escreve tudo o que o trabalho produziu: o tamanho e o hash do
     * ficheiro (as colunas que a etapa 02 deixou nulas), os campos extraídos com a sua
     * confiança, a projeção, a transição e a conclusão do claim — tudo, ou nada.
     */
    private void complete(UUID documentId, String storageKey, byte[] content, ExtractionResult result) {
        transactions.execute(status -> {
            Document document =
                    documents.findById(documentId).orElseThrow(() -> new DocumentNotFoundException(documentId));

            document.recordUploadedFile(content.length, sha256Hex(content));
            document.projectInvoiceFields(result.invoiceFields());
            writeExtractedFields(document, result);

            ValidationSummary summary =
                    validationEngine.validate(validationContexts.build(document, result.confidences()));
            DocumentStatus target = summary.requiresReview() ? DocumentStatus.NEEDS_REVIEW : DocumentStatus.EXTRACTED;
            events.save(document.transitionTo(target, Actor.system(), truncate(summary.reason())));

            claims.findById(storageKey).orElseThrow().complete();
            return null;
        });
    }

    private void writeExtractedFields(Document document, ExtractionResult result) {
        result.confidences().forEach((fieldName, confidence) -> {
            String text = asText(result.invoiceFields(), fieldName);
            if (text != null) {
                ExtractedField.readByMachine(
                        document,
                        fieldName,
                        text,
                        confidence,
                        result.geometries().get(fieldName));
            }
        });
    }

    private static String asText(InvoiceFields fields, ExtractedFieldName fieldName) {
        return switch (fieldName) {
            case SUPPLIER_NAME -> fields.supplierName();
            case SUPPLIER_TAX_ID -> fields.supplierTaxId();
            case INVOICE_NUMBER -> fields.invoiceNumber();
            case ISSUE_DATE ->
                fields.issueDate() == null ? null : fields.issueDate().toString();
            case NET_AMOUNT ->
                fields.netAmount() == null ? null : fields.netAmount().toPlainString();
            case VAT_AMOUNT ->
                fields.vatAmount() == null ? null : fields.vatAmount().toPlainString();
            case VAT_RATE -> fields.vatRate() == null ? null : fields.vatRate().toPlainString();
            case TOTAL_AMOUNT ->
                fields.totalAmount() == null ? null : fields.totalAmount().toPlainString();
            default -> null;
        };
    }

    /**
     * Passa o documento a {@code FAILED} com a razão. Se já não estiver em
     * {@code PROCESSING} — outra entrega concluiu-o entretanto — não é preciso: o estado
     * já diz outra coisa, e melhor.
     */
    private void fail(UUID documentId, String reason) {
        try {
            transactions.execute(status -> {
                Document document = documents.findById(documentId).orElse(null);
                if (document == null) {
                    return null;
                }
                if (document.getStatus() == DocumentStatus.PROCESSING) {
                    events.save(document.transitionTo(DocumentStatus.FAILED, Actor.system(), truncate(reason)));
                } else {
                    log.info("Documento {} já não estava em PROCESSING; o FAILED já não se aplica", documentId);
                }
                return null;
            });
        } catch (RuntimeException e) {
            // Nem o FAILED se conseguiu escrever. A mensagem segue para a DLQ na mesma;
            // o reprocessamento manual é o caminho de recuperação, e é por isso que ele
            // existe.
            log.error("Documento {}: impossível registar o FAILED", documentId, e);
        }
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não existe nesta JVM", e);
        }
    }

    private enum Decision {

        /** A chave não tem documento — a mensagem não tem nada a quem agarrar-se. */
        NOT_FOUND,

        /** Trabalho já concluído — duplicado verdadeiro. */
        ALREADY_DONE,

        /** Já falhou antes — deixar a mensagem seguir o seu caminho até à DLQ. */
        LEAVE,

        /** Claim novo: o documento passou a PROCESSING nesta entrega. */
        PROCEED,

        /** Claim antigo incompleto e documento em PROCESSING: o trabalho retoma. */
        RESUME
    }

    private record Claim(Decision decision, UUID documentId, String contentType) {}

    private static final class DuplicateClaimException extends RuntimeException {}
}
