package com.docgrid.document;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca os documentos de uma exportação como {@link DocumentStatus#EXPORTED}.
 *
 * <p>Existe no pacote {@code document} porque mexe no estado dos documentos, não no ficheiro
 * CSV nem no arquivo S3. O ciclo de vida dos documentos exige que cada transição de estado
 * produza um evento de auditoria, e é aqui que isso acontece para a exportação.
 *
 * <p>O serviço de exportação em {@code com.docgrid.export} chama este depois de tudo o
 * resto estar tratado — CSV gerado, ficheiro no S3. Se a marcação falhar a meio, a
 * transação aborta e o export não fica registado.
 */
@Service
public class DocumentExportService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExportService.class);

    private final DocumentRepository documents;
    private final DocumentEventRepository events;

    DocumentExportService(DocumentRepository documents, DocumentEventRepository events) {
        this.documents = documents;
        this.events = events;
    }

    /**
     * Marca cada documento da lista como {@code EXPORTED} e regista o evento de auditoria
     * correspondente.
     *
     * @param organizationId a organização a que os documentos pertencem
     * @param exportId       o identificador da exportação (surge no motivo e no documento)
     * @param documentIds    os documentos a marcar
     * @param actor          quem desencadeou a exportação
     * @return o número de documentos efetivamente marcados
     * @throws com.docgrid.document.DocumentNotFoundException se algum dos documentos não
     *         existir ou pertencer a outra organização
     */
    @Transactional
    public int markExported(UUID organizationId, UUID exportId, List<UUID> documentIds, Actor actor) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(exportId, "exportId");
        Objects.requireNonNull(documentIds, "documentIds");
        Objects.requireNonNull(actor, "actor");

        int count = 0;
        for (UUID documentId : documentIds) {
            Document document = documents
                    .findByIdAndOrganizationId(documentId, organizationId)
                    .orElseThrow(() -> new DocumentNotFoundException(documentId));

            events.save(document.transitionTo(DocumentStatus.EXPORTED, actor, "Exportação " + exportId));
            document.markExported(exportId);
            count++;
        }

        log.info("Marcados {} documentos como EXPORTED na exportação {}", count, exportId);
        return count;
    }
}
