package com.docgrid.document;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lê o estado persistido de um documento a partir de testes de outros pacotes (o
 * pipeline), sem abrir a visibilidade das entidades nem dos repositórios de
 * {@code com.docgrid.document}. Lê a base de dados diretamente porque é isso que um
 * teste de integração quer afirmar: o que ficou mesmo gravado.
 */
public final class DocumentProbe {

    private DocumentProbe() {}

    public static String status(JdbcTemplate jdbc, UUID documentId) {
        return jdbc.queryForObject("select status from documents where id = ?", String.class, documentId);
    }

    public static Long sizeBytes(JdbcTemplate jdbc, UUID documentId) {
        return jdbc.queryForObject("select size_bytes from documents where id = ?", Long.class, documentId);
    }

    public static String fileHash(JdbcTemplate jdbc, UUID documentId) {
        return jdbc.queryForObject("select file_hash from documents where id = ?", String.class, documentId);
    }

    public static int extractedFieldCount(JdbcTemplate jdbc, UUID documentId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from extracted_fields where document_id = ?", Integer.class, documentId);
        return count == null ? 0 : count;
    }

    public static int eventCount(JdbcTemplate jdbc, UUID documentId, String toStatus) {
        Integer count = jdbc.queryForObject(
                "select count(*) from document_events where document_id = ? and to_status = ?",
                Integer.class,
                documentId,
                toStatus);
        return count == null ? 0 : count;
    }
}
