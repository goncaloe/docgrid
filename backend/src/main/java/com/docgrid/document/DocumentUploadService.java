package com.docgrid.document;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docgrid.auth.CurrentUserProvider;
import com.docgrid.document.dto.FileUrlResponse;
import com.docgrid.document.dto.UploadUrlRequest;
import com.docgrid.document.dto.UploadUrlResponse;
import com.docgrid.storage.PresignedUrl;
import com.docgrid.storage.StorageService;

/**
 * Emite autorizações para o browser falar diretamente com o S3, e regista o documento no
 * momento em que o faz — nada sobe sem rasto.
 *
 * <p>O tamanho e o hash do ficheiro não se conhecem aqui: o ficheiro ainda não subiu.
 * Ficam para a etapa 03, quando o worker lê o objeto para o processar. Ver
 * {@code docs/adr/0005-upload-com-url-pre-assinado.md}.
 */
@Service
public class DocumentUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);

    private final DocumentRepository documents;
    private final DocumentEventRepository events;
    private final StorageService storage;
    private final CurrentUserProvider currentUser;
    private final Clock clock;
    private final Duration urlTtl;
    private final long maxFileSizeBytes;
    private final Map<String, String> allowedTypes;

    DocumentUploadService(
            DocumentRepository documents,
            DocumentEventRepository events,
            StorageService storage,
            CurrentUserProvider currentUser,
            Clock clock,
            UploadProperties properties) {
        this.documents = documents;
        this.events = events;
        this.storage = storage;
        this.currentUser = currentUser;
        this.clock = clock;
        this.urlTtl = properties.urlTtl();
        this.maxFileSizeBytes = properties.maxFileSize().toBytes();
        this.allowedTypes = properties.allowedTypes();
    }

    /**
     * Regista um documento em {@code UPLOADED} e devolve a autorização para o cliente lhe
     * enviar o ficheiro. O registo e o evento {@code CREATED} ficam na mesma transação.
     *
     * @throws UploadValidationException se o tipo, a extensão ou o tamanho não servirem
     */
    @Transactional
    public UploadUrlResponse authorizeUpload(UploadUrlRequest request) {
        String contentType = request.contentType();
        String extension = validate(request);

        UUID organizationId = currentUser.currentOrganizationId();
        UUID submittedBy = currentUser.currentUserId();

        Document document = Document.forUpload(
                organizationId,
                submittedBy,
                cleanFilename(request.filename()),
                contentType,
                keyPrefix(organizationId),
                extension);

        // O id do documento é o fio que liga os logs desta chamada aos do worker
        // que mais tarde processa o mesmo documento — ver application.yml.
        MDC.put("documentId", document.getId().toString());
        try {
            documents.save(document);
            events.save(DocumentEvent.created(document.getId(), Actor.user(submittedBy)));

            PresignedUrl url = storage.createUploadUrl(document.getStorageKey(), contentType, urlTtl);
            log.info("Documento {} registado em UPLOADED, chave {}", document.getId(), document.getStorageKey());
            return new UploadUrlResponse(
                    document.getId(),
                    document.getStorageKey(),
                    url.url(),
                    url.httpMethod(),
                    url.requiredHeaders(),
                    url.expiresAt());
        } finally {
            MDC.remove("documentId");
        }
    }

    /**
     * Uma autorização de leitura do ficheiro de um documento.
     *
     * @throws DocumentNotFoundException se o documento não existir
     */
    @Transactional(readOnly = true)
    public FileUrlResponse fileUrl(UUID documentId) {
        MDC.put("documentId", documentId.toString());
        try {
            Document document =
                    documents.findById(documentId).orElseThrow(() -> new DocumentNotFoundException(documentId));
            PresignedUrl url =
                    storage.createDownloadUrl(document.getStorageKey(), document.getOriginalFilename(), urlTtl);
            return new FileUrlResponse(url.url(), url.expiresAt());
        } finally {
            MDC.remove("documentId");
        }
    }

    private String validate(UploadUrlRequest request) {
        String canonicalExtension = allowedTypes.get(request.contentType());
        if (canonicalExtension == null) {
            throw new UploadValidationException("Tipo de ficheiro não aceite: %s. Aceites: %s"
                    .formatted(
                            request.contentType(),
                            allowedTypes.keySet().stream().sorted().collect(Collectors.joining(", "))));
        }
        String extension = fileExtension(request.filename());
        if (!normalise(extension).equals(canonicalExtension)) {
            throw new UploadValidationException(
                    "A extensão \"%s\" não corresponde ao tipo %s".formatted(extension, request.contentType()));
        }
        long size = request.sizeBytes() == null ? 0L : request.sizeBytes();
        if (size <= 0) {
            throw new UploadValidationException("O tamanho do ficheiro tem de ser positivo");
        }
        if (size > maxFileSizeBytes) {
            throw new UploadValidationException(
                    "Ficheiro grande demais: %d bytes; o máximo é %d".formatted(size, maxFileSizeBytes));
        }
        return canonicalExtension;
    }

    private String keyPrefix(UUID organizationId) {
        LocalDate today = LocalDate.now(clock);
        return "org/%s/%d/%02d".formatted(organizationId, today.getYear(), today.getMonthValue());
    }

    private static String fileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1);
    }

    private static String normalise(String extension) {
        String lower = extension.toLowerCase(Locale.ROOT);
        return lower.equals("jpeg") ? "jpg" : lower;
    }

    /** O nome fica para mostrar ao utilizador; nunca entra na chave do S3. Ainda assim, limpa-se. */
    private static String cleanFilename(String raw) {
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1)
                .replaceAll("\\p{Cntrl}", "")
                .trim();
        if (name.isEmpty()) {
            name = "documento";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
